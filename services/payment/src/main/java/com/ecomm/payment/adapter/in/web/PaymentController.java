package com.ecomm.payment.adapter.in.web;

import com.ecomm.commons.idempotency.IdempotentCommand;
import com.ecomm.commons.security.CurrentCustomer;
import com.ecomm.payment.application.port.in.AuthorizePaymentUseCase;
import com.ecomm.payment.application.port.in.FindPaymentUseCase;
import com.ecomm.payment.application.port.in.VoidPaymentUseCase;
import com.ecomm.payment.domain.InvalidPaymentException;
import com.ecomm.payment.domain.PaymentGatewayUnavailableException;
import com.ecomm.payment.domain.PaymentNotVoidableException;
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
 * The checkout Saga authorizes, voids and reads a Customer's Payments with Orchestration's own
 * {@code ORCHESTRATION} token, naming the Customer in the body, or as {@code customerId} when it
 * reads one. Checkout's {@code CHECKOUT} token authorizes and voids too, until Checkout moves onto
 * the Saga. The Customer reads them back with a {@code CUSTOMER} token, whose {@code sub} must own
 * the Payment; Staff read them under {@code /staff/payments}.
 */
@RestController
@RequestMapping("/payments")
class PaymentController {

  private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

  private final AuthorizePaymentUseCase authorize;
  private final VoidPaymentUseCase voidPayment;
  private final FindPaymentUseCase find;

  PaymentController(
      AuthorizePaymentUseCase authorize, VoidPaymentUseCase voidPayment, FindPaymentUseCase find) {
    this.authorize = authorize;
    this.voidPayment = voidPayment;
    this.find = find;
  }

  /**
   * 201 with the recorded Payment, authorized or declined, and its URL in {@code Location}; 502
   * when the gateway fails to answer. The {@code Idempotency-Key} is required, and is passed on to
   * the gateway; a repeat replays the first response and authorizes nothing more.
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('CHECKOUT', 'ORCHESTRATION')")
  @IdempotentCommand
  ResponseEntity<PaymentResponse> authorize(
      // Never null here: @IdempotentCommand refuses a request without one.
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody AuthorizePaymentRequest request) {
    var payment = authorize.authorize(request.toAuthorizationRequest(), idempotencyKey);
    log.info(
        "Recorded Payment {} for Order {} as {}",
        payment.id(),
        payment.orderId(),
        payment.status());
    return ResponseEntity.created(URI.create("/payments/" + payment.id()))
        .body(PaymentResponse.of(payment));
  }

  /**
   * 200 with the voided Payment, whether this void released it or an earlier one did; 409 for a
   * declined Payment; 404 for an unknown one or another Customer's; 502 when the gateway fails to
   * answer.
   */
  @PostMapping("/{id}/void")
  @PreAuthorize("hasAnyRole('CHECKOUT', 'ORCHESTRATION')")
  PaymentResponse voidPayment(@PathVariable String id, @RequestBody VoidPaymentRequest request) {
    var customerId = request.customer();
    var payment =
        parse(id)
            .flatMap(paymentId -> voidPayment.voidPayment(customerId, paymentId))
            .orElseThrow(() -> new PaymentNotFoundException(id));
    log.info("Payment {} for Order {} is {}", payment.id(), payment.orderId(), payment.status());
    return PaymentResponse.of(payment);
  }

  /**
   * 404 for an unknown ID, one that isn't a UUID, or another Customer's Payment, so the answer
   * never reveals that someone else's Payment exists.
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('CUSTOMER')")
  PaymentResponse payment(CurrentCustomer customer, @PathVariable String id) {
    return ownedPayment(customer.id(), id);
  }

  /**
   * The named Customer's Payment, as the checkout Saga reads it while it awaits settlement; 404 as
   * for the Customer's own read.
   */
  @GetMapping(path = "/{id}", params = "customerId")
  @PreAuthorize("hasRole('ORCHESTRATION')")
  PaymentResponse customersPayment(@PathVariable String id, @RequestParam String customerId) {
    return ownedPayment(customerId, id);
  }

  /** The Customer's Payments for one of their Orders, newest first; empty for anyone else's. */
  @GetMapping
  @PreAuthorize("hasRole('CUSTOMER')")
  List<PaymentResponse> payments(CurrentCustomer customer, @RequestParam String orderId) {
    return find.payments(customer.id(), orderId).stream().map(PaymentResponse::of).toList();
  }

  private PaymentResponse ownedPayment(String customerId, String id) {
    return parse(id)
        .flatMap(paymentId -> find.payment(customerId, paymentId))
        .map(PaymentResponse::of)
        .orElseThrow(() -> new PaymentNotFoundException(id));
  }

  private static Optional<UUID> parse(String id) {
    try {
      return Optional.of(UUID.fromString(id));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  ProblemDetail notFound(PaymentNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(PaymentNotVoidableException.class)
  ProblemDetail notVoidable(PaymentNotVoidableException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("reason", "paymentNotVoidable");
    return problem;
  }

  @ExceptionHandler(PaymentGatewayUnavailableException.class)
  ProblemDetail gatewayUnavailable(PaymentGatewayUnavailableException e) {
    log.warn("The payment gateway failed to answer", e);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_GATEWAY, "The payment gateway failed to answer; nothing was recorded.");
  }

  @ExceptionHandler(InvalidPaymentException.class)
  ProblemDetail invalid(InvalidPaymentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
