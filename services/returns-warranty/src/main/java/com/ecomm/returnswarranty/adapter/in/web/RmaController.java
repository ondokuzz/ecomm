package com.ecomm.returnswarranty.adapter.in.web;

import com.ecomm.commons.idempotency.IdempotentCommand;
import com.ecomm.returnswarranty.adapter.in.web.LifecycleBodies.HoldBody;
import com.ecomm.returnswarranty.adapter.in.web.LifecycleBodies.InspectBody;
import com.ecomm.returnswarranty.adapter.in.web.LifecycleBodies.ReceiveBody;
import com.ecomm.returnswarranty.adapter.in.web.LifecycleBodies.ResolveBody;
import com.ecomm.returnswarranty.application.port.in.Page;
import com.ecomm.returnswarranty.application.port.in.RmaFilter;
import com.ecomm.returnswarranty.application.port.in.RmaUseCase;
import com.ecomm.returnswarranty.domain.Rma;
import com.ecomm.returnswarranty.domain.RmaExceptions.NoWarrantyException;
import com.ecomm.returnswarranty.domain.RmaExceptions.NotDeliveredException;
import com.ecomm.returnswarranty.domain.RmaExceptions.OrderNotKnownYetException;
import com.ecomm.returnswarranty.domain.RmaExceptions.OutcomeNotForKindException;
import com.ecomm.returnswarranty.domain.RmaExceptions.OutsideWindowException;
import com.ecomm.returnswarranty.domain.RmaExceptions.ProductNotKnownYetException;
import com.ecomm.returnswarranty.domain.RmaExceptions.QuantityExceededException;
import com.ecomm.returnswarranty.domain.RmaExceptions.QuantityMismatchException;
import com.ecomm.returnswarranty.domain.RmaExceptions.ResolutionContradictsInspectionException;
import com.ecomm.returnswarranty.domain.RmaExceptions.RmaAlreadyDecidedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialMismatchException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialNumberAlreadyClaimedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialNumberNotShippedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.SerialNumbersMiscountedException;
import com.ecomm.returnswarranty.domain.RmaExceptions.TransitionRefusedException;
import com.ecomm.returnswarranty.domain.RmaStatus;
import com.ecomm.returnswarranty.domain.Window;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RMAs. A Customer requests one for a delivered Variant of their Order, with an {@code
 * Idempotency-Key}, reads their own, and asks what their Order's lines are still eligible for.
 * Staff decide them, receive their units and inspect them, and read any. Orchestration's returns
 * Saga, with its own {@code ORCHESTRATION} token, resolves, holds and expires them. Every step
 * after a decision takes a required {@code Idempotency-Key}, so a repeat replays its answer.
 */
@RestController
@RequestMapping("/rmas")
class RmaController {

  private static final Logger log = LoggerFactory.getLogger(RmaController.class);

  /** The most RMAs a page can hold. */
  static final int MAX_PAGE_SIZE = 100;

  /** How long a Customer waits before asking again while the projections catch up. */
  private static final String RETRY_AFTER_SECONDS = "5";

  private final RmaUseCase rmas;

  RmaController(RmaUseCase rmas) {
    this.rmas = rmas;
  }

  /**
   * 201 with the RMA, {@code REQUESTED}, and its URL in {@code Location}. 404 when the Order has no
   * delivered Shipment of the Customer's; 422 with a {@code reason} for each refusal.
   */
  @PostMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  @IdempotentCommand
  ResponseEntity<RmaResponse> request(@RequestBody RequestRmaBody body, Authentication customer) {
    var rma = rmas.request(body.toRequest(customer.getName()));
    log.info(
        "Requested {} {} for {} of Variant {} on Order {}",
        rma.kind(),
        rma.reference(),
        rma.quantity(),
        rma.variantId(),
        rma.orderId());
    return ResponseEntity.created(URI.create("/rmas/" + rma.id())).body(RmaResponse.of(rma));
  }

  record ApproveBody(String note) {}

  record RejectBody(String reason) {}

  /**
   * Staff approve a {@code REQUESTED} RMA, with an optional note of up to 1,000 characters; 409
   * {@code rmaAlreadyDecided} once it is decided. A repeat is refused rather than replayed, so it
   * takes no {@code Idempotency-Key}.
   */
  @PostMapping("/{id}/approve")
  @PreAuthorize("hasRole('STAFF')")
  RmaResponse approve(
      @PathVariable String id,
      @RequestBody(required = false) ApproveBody body,
      @AuthenticationPrincipal Jwt staff) {
    var note = body == null ? null : body.note();
    if (note != null && (note.isBlank() || note.length() > RequestRmaBody.MAX_TEXT)) {
      throw new InvalidRequestException(
          "note", "must be 1 to " + RequestRmaBody.MAX_TEXT + " characters when given");
    }
    return stepped(id, uuid -> rmas.approve(uuid, staff.getSubject(), note));
  }

  /**
   * Staff reject a {@code REQUESTED} RMA, with a reason of 1 to 1,000 characters; 409 {@code
   * rmaAlreadyDecided} once it is decided.
   */
  @PostMapping("/{id}/reject")
  @PreAuthorize("hasRole('STAFF')")
  RmaResponse reject(
      @PathVariable String id,
      @RequestBody(required = false) RejectBody body,
      @AuthenticationPrincipal Jwt staff) {
    var reason = RequestRmaBody.text("reason", body == null ? null : body.reason());
    return stepped(id, uuid -> rmas.reject(uuid, staff.getSubject(), reason));
  }

  /** The RMA the step changed; 404 when there is no such RMA. */
  private RmaResponse stepped(String id, Function<UUID, Optional<Rma>> step) {
    var rma = parse(id).flatMap(step).orElseThrow(() -> new RmaNotFoundException(id));
    log.info("{} is {}", rma.reference(), rma.status());
    return RmaResponse.of(rma);
  }

  /**
   * Staff receive an {@code APPROVED} RMA's units: each by Serial number, or by quantity for a
   * Variant without Serial tracking. 422 {@code serialMismatch} naming each Serial number that
   * isn't the RMA's, or {@code quantityMismatch} when they don't number its units; 409 {@code
   * transitionRefused} from any other status.
   */
  @PostMapping("/{id}/receive")
  @PreAuthorize("hasRole('STAFF')")
  @IdempotentCommand
  RmaResponse receive(
      @PathVariable String id,
      @RequestBody(required = false) ReceiveBody body,
      @AuthenticationPrincipal Jwt staff) {
    var units = body == null ? new ReceiveBody(null, null) : body;
    var serials = units.serials();
    return stepped(id, uuid -> rmas.receive(uuid, staff.getSubject(), serials, units.units()));
  }

  /**
   * Staff record a {@code RECEIVED} RMA's Inspection: {@code ACCEPTED} or {@code REFUSED} for a
   * return, {@code FAULT_CONFIRMED} or {@code NO_FAULT_FOUND} for a warranty claim, with a note
   * that a refusing outcome must give. 422 {@code outcomeNotForKind} for the other kind's; 409
   * {@code transitionRefused} from any other status.
   */
  @PostMapping("/{id}/inspect")
  @PreAuthorize("hasRole('STAFF')")
  @IdempotentCommand
  RmaResponse inspect(
      @PathVariable String id,
      @RequestBody(required = false) InspectBody body,
      @AuthenticationPrincipal Jwt staff) {
    var inspection = body == null ? new InspectBody(null, null) : body;
    var outcome = inspection.toOutcome();
    return stepped(id, uuid -> rmas.inspect(uuid, staff.getSubject(), outcome, inspection.note()));
  }

  /** A resolved RMA, and whether every unit its Order delivered has now been refunded. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record ResolvedResponse(@JsonUnwrapped RmaResponse rma, boolean orderFullyRefunded) {}

  /**
   * The returns Saga resolves an {@code INSPECTED} RMA. 422 {@code resolutionContradictsInspection}
   * for a resolution its Inspection doesn't allow; 409 {@code transitionRefused} from any other
   * status.
   */
  @PostMapping("/{id}/resolve")
  @PreAuthorize("hasRole('ORCHESTRATION')")
  @IdempotentCommand
  ResolvedResponse resolve(
      @PathVariable String id, @RequestBody(required = false) ResolveBody body) {
    var resolve = body == null ? new ResolveBody(null, null, null, null) : body;
    var resolution = resolve.toResolution();
    var reference = resolution == RmaStatus.REFUNDED ? resolve.paymentTransactionReference() : null;
    var replacement = resolution == RmaStatus.EXCHANGED ? resolve.replacement() : null;
    var resolved =
        parse(id)
            .flatMap(uuid -> rmas.resolve(uuid, resolution, reference, replacement, resolve.note()))
            .orElseThrow(() -> new RmaNotFoundException(id));
    log.info(
        "{} is {}; its Order is {}fully refunded",
        resolved.rma().reference(),
        resolved.rma().status(),
        resolved.orderFullyRefunded() ? "" : "not ");
    return new ResolvedResponse(RmaResponse.of(resolved.rma()), resolved.orderFullyRefunded());
  }

  /**
   * The returns Saga holds an {@code INSPECTED} RMA, with a reason of 1 to 1,000 characters; 409
   * {@code transitionRefused} from any other status.
   */
  @PostMapping("/{id}/hold")
  @PreAuthorize("hasRole('ORCHESTRATION')")
  @IdempotentCommand
  RmaResponse hold(@PathVariable String id, @RequestBody(required = false) HoldBody body) {
    var reason = RequestRmaBody.text("reason", body == null ? null : body.reason());
    return stepped(id, uuid -> rmas.hold(uuid, reason));
  }

  /**
   * The returns Saga expires an {@code APPROVED} RMA once its Send-back deadline passed; 409 {@code
   * transitionRefused} from any other status, so a receipt that raced the timer wins.
   */
  @PostMapping("/{id}/expire")
  @PreAuthorize("hasRole('ORCHESTRATION')")
  @IdempotentCommand
  RmaResponse expire(@PathVariable String id) {
    return stepped(id, rmas::expire);
  }

  /** One delivered line of an Order, and what it is still eligible for. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record EligibilityResponse(
      String variantId,
      String productName,
      String returnPeriodEndsAt,
      String warrantyWindowEndsAt,
      List<String> serialNumbers,
      int quantity) {}

  record EligibilitiesResponse(String orderId, List<EligibilityResponse> lines) {}

  /**
   * What each line the Customer's delivered Order delivered is still eligible for: when its Return
   * Period ends, when its Warranty Window ends (left out when its Product has none, or isn't known
   * yet), and the Serial numbers and quantity still free to claim. 404 when the Order has no
   * delivered Shipment of the Customer's.
   */
  @GetMapping("/eligibility")
  @PreAuthorize("hasRole('CUSTOMER')")
  EligibilitiesResponse eligibility(@RequestParam String orderId, Authentication customer) {
    return new EligibilitiesResponse(
        orderId,
        rmas.eligibility(customer.getName(), orderId).stream()
            .map(
                e ->
                    new EligibilityResponse(
                        e.variantId(),
                        e.productName(),
                        e.returnPeriodEndsAt().toString(),
                        text(e.warrantyWindowEndsAt()),
                        e.available().serialNumbers(),
                        e.available().quantity()))
            .toList());
  }

  private static String text(Instant instant) {
    return instant == null ? null : instant.toString();
  }

  /** The RMA. A Customer reads only their own: another's is a 404. */
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF')")
  RmaResponse rma(@PathVariable String id, Authentication caller) {
    return parse(id)
        .flatMap(rmas::rma)
        .filter(rma -> isStaff(caller) || rma.belongsTo(caller.getName()))
        .map(RmaResponse::of)
        .orElseThrow(() -> new RmaNotFoundException(id));
  }

  /**
   * A page of RMAs: a Customer's own, or for Staff, everyone's (the RMA queue). {@code status}, one
   * or more, repeated or comma-separated, and {@code orderId} narrow it when given; {@code page}
   * counts from 0; {@code size} is 1 to {@value #MAX_PAGE_SIZE}. {@code APPROVED} alone is by
   * Send-back deadline, soonest first; any other list is oldest first.
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF')")
  Page<RmaResponse> rmas(
      @RequestParam(required = false) List<String> status,
      @RequestParam(required = false) String orderId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication caller) {
    if (page < 0) {
      throw new InvalidRequestException("page", "counts from 0");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidRequestException("size", "is 1 to " + MAX_PAGE_SIZE);
    }
    var filter = new RmaFilter(isStaff(caller) ? null : caller.getName(), status(status), orderId);
    return rmas.rmas(filter, page, size).map(RmaResponse::of);
  }

  private static Set<RmaStatus> status(List<String> values) {
    var statuses = new HashSet<RmaStatus>();
    for (var value : values == null ? List.<String>of() : values) {
      try {
        statuses.add(RmaStatus.valueOf(value));
      } catch (IllegalArgumentException e) {
        throw new InvalidRequestException(
            "status", "must be one or more of " + List.of(RmaStatus.values()));
      }
    }
    return statuses;
  }

  private static boolean isStaff(Authentication caller) {
    return caller.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
  }

  private static Optional<UUID> parse(String id) {
    try {
      return Optional.of(UUID.fromString(id));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  static class RmaNotFoundException extends RuntimeException {

    RmaNotFoundException(String id) {
      super("No RMA " + id);
    }
  }

  @ExceptionHandler(RmaNotFoundException.class)
  ProblemDetail notFound(RmaNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(NotDeliveredException.class)
  ProblemDetail notDelivered(NotDeliveredException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(RmaAlreadyDecidedException.class)
  ProblemDetail alreadyDecided(RmaAlreadyDecidedException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("reason", "rmaAlreadyDecided");
    return problem;
  }

  @ExceptionHandler(TransitionRefusedException.class)
  ProblemDetail transitionRefused(TransitionRefusedException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("reason", "transitionRefused");
    problem.setProperty("status", e.status().name());
    return problem;
  }

  @ExceptionHandler(SerialMismatchException.class)
  ProblemDetail serialMismatch(SerialMismatchException e) {
    var problem = refusal(e, "serialMismatch");
    problem.setProperty("serialNumbers", e.serialNumbers());
    return problem;
  }

  @ExceptionHandler(QuantityMismatchException.class)
  ProblemDetail quantityMismatch(QuantityMismatchException e) {
    var problem = refusal(e, "quantityMismatch");
    problem.setProperty("expected", e.expected());
    return problem;
  }

  @ExceptionHandler(OutcomeNotForKindException.class)
  ProblemDetail outcomeNotForKind(OutcomeNotForKindException e) {
    return refusal(e, "outcomeNotForKind");
  }

  @ExceptionHandler(ResolutionContradictsInspectionException.class)
  ProblemDetail contradictsInspection(ResolutionContradictsInspectionException e) {
    return refusal(e, "resolutionContradictsInspection");
  }

  @ExceptionHandler(QuantityExceededException.class)
  ProblemDetail quantityExceeded(QuantityExceededException e) {
    var problem = refusal(e, "quantityExceeded");
    problem.setProperty("available", e.available());
    return problem;
  }

  @ExceptionHandler(SerialNumbersMiscountedException.class)
  ProblemDetail miscounted(SerialNumbersMiscountedException e) {
    var problem = refusal(e, "serialNumbersMiscounted");
    problem.setProperty("expected", e.expected());
    return problem;
  }

  @ExceptionHandler(SerialNumberNotShippedException.class)
  ProblemDetail notShipped(SerialNumberNotShippedException e) {
    var problem = refusal(e, "serialNotShipped");
    problem.setProperty("serialNumber", e.serialNumber());
    return problem;
  }

  @ExceptionHandler(SerialNumberAlreadyClaimedException.class)
  ProblemDetail alreadyClaimed(SerialNumberAlreadyClaimedException e) {
    var problem = refusal(e, "serialAlreadyClaimed");
    problem.setProperty("serialNumber", e.serialNumber());
    return problem;
  }

  @ExceptionHandler(OutsideWindowException.class)
  ProblemDetail outsideWindow(OutsideWindowException e) {
    var problem =
        refusal(
            e,
            e.window().kind() == Window.Kind.RETURN_PERIOD
                ? "outsideReturnPeriod"
                : "outsideWarrantyWindow");
    problem.setProperty("endedAt", e.window().endsAt().toString());
    return problem;
  }

  @ExceptionHandler(NoWarrantyException.class)
  ProblemDetail noWarranty(NoWarrantyException e) {
    return refusal(e, "noWarranty");
  }

  @ExceptionHandler(ProductNotKnownYetException.class)
  ResponseEntity<ProblemDetail> productNotKnownYet(ProductNotKnownYetException e) {
    return notKnownYet(e, "productNotKnownYet");
  }

  @ExceptionHandler(OrderNotKnownYetException.class)
  ResponseEntity<ProblemDetail> orderNotKnownYet(OrderNotKnownYetException e) {
    return notKnownYet(e, "orderNotKnownYet");
  }

  /** A 503 to try again shortly, while a projection catches up. */
  private static ResponseEntity<ProblemDetail> notKnownYet(RuntimeException e, String reason) {
    log.info(e.getMessage());
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    problem.setProperty("reason", reason);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
        .body(problem);
  }

  /** A 422: the request is well-formed, but the RMA refuses it, for {@code reason}. */
  private static ProblemDetail refusal(RuntimeException e, String reason) {
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    problem.setProperty("reason", reason);
    return problem;
  }

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail invalid(InvalidRequestException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setProperty("errors", List.of(e.violation()));
    return problem;
  }
}
