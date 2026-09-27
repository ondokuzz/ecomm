package com.ecomm.payment.adapter.in.web;

import com.ecomm.commons.security.CurrentCustomer;
import com.ecomm.payment.application.port.in.AuthorizePaymentUseCase;
import com.ecomm.payment.application.port.in.FindPaymentUseCase;
import com.ecomm.payment.domain.InvalidPaymentException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout authorizes a Customer's payments with its own {@code CHECKOUT} token, naming the
 * Customer in the body. The Customer reads them back with a {@code CUSTOMER} token, whose {@code
 * sub} must own the Payment; Staff have no Payments.
 */
@RestController
@RequestMapping("/payments")
class PaymentController {

  private final AuthorizePaymentUseCase authorize;
  private final FindPaymentUseCase find;

  PaymentController(AuthorizePaymentUseCase authorize, FindPaymentUseCase find) {
    this.authorize = authorize;
    this.find = find;
  }

  /** 201 with the recorded Payment, and its URL in {@code Location}. */
  @PostMapping
  @PreAuthorize("hasRole('CHECKOUT')")
  ResponseEntity<PaymentResponse> authorize(@RequestBody AuthorizePaymentRequest request) {
    var payment = authorize.authorize(request.toAuthorizationRequest());
    return ResponseEntity.created(URI.create("/payments/" + payment.id()))
        .body(PaymentResponse.of(payment));
  }

  /**
   * 404 for an unknown ID, one that isn't a UUID, or another Customer's Payment, so the answer
   * never reveals that someone else's Payment exists.
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('CUSTOMER')")
  PaymentResponse payment(CurrentCustomer customer, @PathVariable String id) {
    return parse(id)
        .flatMap(paymentId -> find.payment(customer.id(), paymentId))
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

  @ExceptionHandler(InvalidPaymentException.class)
  ProblemDetail invalid(InvalidPaymentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
