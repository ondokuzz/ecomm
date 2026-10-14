package com.ecomm.fulfillmentshipping.adapter.in.web;

import com.ecomm.commons.idempotency.IdempotentCommand;
import com.ecomm.fulfillmentshipping.application.port.in.ChangeShipmentUseCase;
import com.ecomm.fulfillmentshipping.application.port.in.CreateShipmentUseCase;
import com.ecomm.fulfillmentshipping.application.port.in.FindShipmentsUseCase;
import com.ecomm.fulfillmentshipping.application.port.in.Page;
import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.IllegalTransitionException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.InvalidSerialNumbersException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.ProductsNotKnownYetException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.SerialNumberAlreadyShippedException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.ShipmentExistsException;
import com.ecomm.fulfillmentshipping.domain.ShipmentStatus;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shipments. The fulfillment Saga creates, dispatches and holds them with Orchestration's {@code
 * ORCHESTRATION} token and an {@code Idempotency-Key}; the gateway routes none of these. Staff pick
 * and pack them and read the queue with a {@code STAFF} token, as Orchestration reads. A Customer
 * reads their own Order's Shipment.
 */
@RestController
@RequestMapping("/shipments")
class ShipmentController {

  private static final Logger log = LoggerFactory.getLogger(ShipmentController.class);

  /** The most Shipments a page of the queue can hold. */
  static final int MAX_PAGE_SIZE = 100;

  /** How long the Saga waits before asking again while Catalog's Products catch up. */
  private static final String RETRY_AFTER_SECONDS = "5";

  record PackRequest(Map<String, List<String>> serialNumbers) {}

  record HoldRequest(String reason) {}

  private final CreateShipmentUseCase create;
  private final ChangeShipmentUseCase change;
  private final FindShipmentsUseCase find;

  ShipmentController(
      CreateShipmentUseCase create, ChangeShipmentUseCase change, FindShipmentsUseCase find) {
    this.create = create;
    this.change = change;
    this.find = find;
  }

  /**
   * 201 with the Shipment, awaiting pick, and its URL in {@code Location}; 409 {@code
   * shipmentExists} when the Order already has one; 503 {@code productsNotKnownYet}, with {@code
   * Retry-After}, until Catalog's events have brought every Variant's Product.
   */
  @PostMapping
  @PreAuthorize("hasRole('ORCHESTRATION')")
  @IdempotentCommand
  ResponseEntity<ShipmentResponse> create(@RequestBody CreateShipmentRequest request) {
    var shipment = create.create(request.toNewShipment());
    log.info(
        "Created Shipment {} for Order {} from {}",
        shipment.id(),
        shipment.orderId(),
        shipment.warehouseId());
    return ResponseEntity.created(URI.create("/shipments/" + shipment.id()))
        .body(ShipmentResponse.of(shipment));
  }

  /** Books it with the Carrier: {@code PACKED} to {@code SHIPPED}, with its tracking number. */
  @PostMapping("/{id}/dispatch")
  @PreAuthorize("hasRole('ORCHESTRATION')")
  @IdempotentCommand
  ShipmentResponse dispatch(
      @PathVariable String id,
      // Never null here: @IdempotentCommand refuses a request without one.
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
    return changed(id, shipmentId -> change.dispatch(shipmentId, idempotencyKey));
  }

  /** {@code PACKED} to {@code ON_HOLD}, for its Payment's capture was refused with the reason. */
  @PostMapping("/{id}/hold")
  @PreAuthorize("hasRole('ORCHESTRATION')")
  @IdempotentCommand
  ShipmentResponse hold(@PathVariable String id, @RequestBody HoldRequest request) {
    var reason = request.reason();
    if (reason == null || reason.isBlank() || reason.length() > 255) {
      throw new InvalidRequestException("reason", "must be 1 to 255 characters");
    }
    return changed(id, shipmentId -> change.hold(shipmentId, reason));
  }

  /** {@code AWAITING_PICK} to {@code PICKED}, by the Staff member. */
  @PostMapping("/{id}/pick")
  @PreAuthorize("hasRole('STAFF')")
  ShipmentResponse pick(@PathVariable String id, @AuthenticationPrincipal Jwt staff) {
    return changed(id, shipmentId -> change.pick(shipmentId, staff.getSubject()));
  }

  /**
   * {@code PICKED} to {@code PACKED}, by the Staff member, with one Serial number per unit of each
   * serial-tracked line, by Variant ID: {@code {"serialNumbers": {"<variantId>": ["...", ...]}}}.
   * 422 with each violation in {@code errors}; 409 {@code serialAlreadyShipped} when another
   * Shipment carries one.
   */
  @PostMapping("/{id}/pack")
  @PreAuthorize("hasRole('STAFF')")
  ShipmentResponse pack(
      @PathVariable String id,
      @RequestBody PackRequest request,
      @AuthenticationPrincipal Jwt staff) {
    var serialNumbers =
        request.serialNumbers() == null ? Map.<String, List<String>>of() : request.serialNumbers();
    return changed(id, shipmentId -> change.pack(shipmentId, serialNumbers, staff.getSubject()));
  }

  private ShipmentResponse changed(String id, Function<UUID, Optional<Shipment>> move) {
    var shipment = parse(id).flatMap(move).orElseThrow(() -> new ShipmentNotFoundException(id));
    log.info(
        "Shipment {} for Order {} is {}", shipment.id(), shipment.orderId(), shipment.status());
    return ShipmentResponse.of(shipment);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('STAFF', 'ORCHESTRATION')")
  ShipmentResponse shipment(@PathVariable String id) {
    return parse(id)
        .flatMap(find::shipment)
        .map(ShipmentResponse::of)
        .orElseThrow(() -> new ShipmentNotFoundException(id));
  }

  /**
   * The Order's Shipment. A Customer reads only their own: another's, like an Order without one, is
   * a 404.
   */
  @GetMapping(params = "orderId")
  @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORCHESTRATION')")
  ShipmentResponse shipmentOf(@RequestParam String orderId, Authentication caller) {
    return find.shipmentOf(orderId)
        .filter(shipment -> readsAny(caller) || shipment.belongsTo(caller.getName()))
        .map(ShipmentResponse::of)
        .orElseThrow(() -> new ShipmentNotFoundException("of Order " + orderId));
  }

  /**
   * The fulfillment queue: a page of Shipments, oldest first, in {@code status} and from {@code
   * warehouseId} when given. {@code page} counts from 0; {@code size} is 1 to {@value
   * #MAX_PAGE_SIZE}.
   */
  @GetMapping(params = "!orderId")
  @PreAuthorize("hasAnyRole('STAFF', 'ORCHESTRATION')")
  Page<ShipmentResponse> shipments(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String warehouseId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 0) {
      throw new InvalidRequestException("page", "counts from 0");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidRequestException("size", "is 1 to " + MAX_PAGE_SIZE);
    }
    return find.shipments(status(status), warehouseId, page, size).map(ShipmentResponse::of);
  }

  private static ShipmentStatus status(String value) {
    if (value == null) {
      return null;
    }
    try {
      return ShipmentStatus.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException(
          "status", "must be one of " + List.of(ShipmentStatus.values()));
    }
  }

  private static boolean readsAny(Authentication caller) {
    return caller.getAuthorities().stream()
        .anyMatch(
            a ->
                a.getAuthority().equals("ROLE_STAFF")
                    || a.getAuthority().equals("ROLE_ORCHESTRATION"));
  }

  private static Optional<UUID> parse(String id) {
    try {
      return Optional.of(UUID.fromString(id));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  static class ShipmentNotFoundException extends RuntimeException {

    ShipmentNotFoundException(String id) {
      super("No Shipment " + id);
    }
  }

  @ExceptionHandler(ShipmentNotFoundException.class)
  ProblemDetail notFound(ShipmentNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(IllegalTransitionException.class)
  ProblemDetail illegalTransition(IllegalTransitionException e) {
    return problem(HttpStatus.CONFLICT, e, "illegalTransition");
  }

  @ExceptionHandler(ShipmentExistsException.class)
  ProblemDetail exists(ShipmentExistsException e) {
    return problem(HttpStatus.CONFLICT, e, "shipmentExists");
  }

  @ExceptionHandler(SerialNumberAlreadyShippedException.class)
  ProblemDetail alreadyShipped(SerialNumberAlreadyShippedException e) {
    var problem = problem(HttpStatus.CONFLICT, e, "serialAlreadyShipped");
    problem.setProperty("serialNumber", e.serialNumber());
    return problem;
  }

  @ExceptionHandler(InvalidSerialNumbersException.class)
  ProblemDetail invalidSerials(InvalidSerialNumbersException e) {
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    problem.setProperty("errors", e.violations());
    return problem;
  }

  @ExceptionHandler(ProductsNotKnownYetException.class)
  ResponseEntity<ProblemDetail> productsNotKnownYet(ProductsNotKnownYetException e) {
    log.info(e.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
        .body(problem(HttpStatus.SERVICE_UNAVAILABLE, e, "productsNotKnownYet"));
  }

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail invalid(InvalidRequestException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setProperty("errors", List.of(e.violation()));
    return problem;
  }

  private static ProblemDetail problem(HttpStatus status, RuntimeException e, String reason) {
    var problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
    problem.setProperty("reason", reason);
    return problem;
  }
}
