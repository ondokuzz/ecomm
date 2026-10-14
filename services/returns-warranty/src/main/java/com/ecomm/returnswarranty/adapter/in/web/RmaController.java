package com.ecomm.returnswarranty.adapter.in.web;

import com.ecomm.commons.idempotency.IdempotentCommand;
import com.ecomm.returnswarranty.application.port.in.RmaUseCase;
import com.ecomm.returnswarranty.domain.RmaExceptions.NotDeliveredException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RMAs. A Customer requests one for a delivered Variant of their Order, with an {@code
 * Idempotency-Key}, and reads their own. Staff decide them and read any.
 */
@RestController
@RequestMapping("/rmas")
class RmaController {

  private static final Logger log = LoggerFactory.getLogger(RmaController.class);

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

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail invalid(InvalidRequestException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    problem.setProperty("errors", List.of(e.violation()));
    return problem;
  }
}
