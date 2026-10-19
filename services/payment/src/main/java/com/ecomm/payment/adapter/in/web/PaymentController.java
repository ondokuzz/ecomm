package com.ecomm.payment.adapter.in.web;

import com.ecomm.commons.idempotency.IdempotentCommand;
import com.ecomm.commons.security.CurrentCustomer;
import com.ecomm.payment.application.port.in.AuthorizePaymentUseCase;
import com.ecomm.payment.application.port.in.CapturePaymentUseCase;
import com.ecomm.payment.application.port.in.CapturePaymentUseCase.CaptureOutcome;
import com.ecomm.payment.application.port.in.FindPaymentUseCase;
import com.ecomm.payment.application.port.in.RefundPaymentUseCase;
import com.ecomm.payment.application.port.in.RefundPaymentUseCase.RefundOutcome;
import com.ecomm.payment.application.port.in.VoidPaymentUseCase;
import com.ecomm.payment.domain.InvalidPaymentException;
import com.ecomm.payment.domain.PaymentGatewayUnavailableException;
import com.ecomm.payment.domain.PaymentNotCapturableException;
import com.ecomm.payment.domain.PaymentNotRefundableException;
import com.ecomm.payment.domain.PaymentNotVoidableException;
import com.ecomm.payment.domain.RefundExceedsCapturedException;
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
 * The checkout Saga authorizes, voids and reads a Customer's Payments, the fulfillment Saga
 * captures them, and the returns Saga refunds them, with Orchestration's own {@code ORCHESTRATION}
 * token, naming the Customer in the body, or as {@code customerId} when it reads one. The Customer
 * reads them back with a {@code CUSTOMER} token, whose {@code sub} must own the Payment; Staff read
 * them under {@code /staff/payments}.
 */
@RestController
@RequestMapping("/payments")
class PaymentController {

  private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

  private final AuthorizePaymentUseCase authorize;
  private final VoidPaymentUseCase voidPayment;
  private final CapturePaymentUseCase capture;
  private final RefundPaymentUseCase refund;
  private final FindPaymentUseCase find;

  PaymentController(
      AuthorizePaymentUseCase authorize,
      VoidPaymentUseCase voidPayment,
      CapturePaymentUseCase capture,
      RefundPaymentUseCase refund,
      FindPaymentUseCase find) {
    this.authorize = authorize;
    this.voidPayment = voidPayment;
    this.capture = capture;
    this.refund = refund;
    this.find = find;
  }

  /**
   * 201 with the recorded Payment, authorized or declined, and its URL in {@code Location}; 502
   * when the gateway fails to answer. The {@code Idempotency-Key} is required, and is passed on to
   * the gateway; a repeat replays the first response and authorizes nothing more.
   */
  @PostMapping
  @PreAuthorize("hasRole('ORCHESTRATION')")
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
   * 200 with the captured Payment, whether this capture charged it or an earlier one did, even if
   * it has been refunded since; 402 {@code captureDeclined} with the gateway's {@code
   * declineReason} when the gateway refuses, the refusal recorded and the Payment still authorized;
   * 409 {@code paymentNotCapturable} for a pending, declined or voided Payment; 404 for an unknown
   * one or another Customer's; 502 when the gateway fails to answer, with nothing recorded. The
   * {@code Idempotency-Key} is required, and is passed on to the gateway.
   */
  @PostMapping("/{id}/capture")
  @PreAuthorize("hasRole('ORCHESTRATION')")
  @IdempotentCommand
  ResponseEntity<?> capture(
      @PathVariable String id,
      // Never null here: @IdempotentCommand refuses a request without one.
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody CapturePaymentRequest request) {
    var customerId = request.customer();
    var result =
        parse(id)
            .flatMap(paymentId -> capture.capture(customerId, paymentId, idempotencyKey))
            .orElseThrow(() -> new PaymentNotFoundException(id));
    var payment = result.payment();
    log.info("Payment {} for Order {} is {}", payment.id(), payment.orderId(), payment.status());
    if (result.outcome() == CaptureOutcome.DECLINED) {
      var problem =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.PAYMENT_REQUIRED,
              "The gateway declined to capture Payment " + payment.id() + "; it stays authorized.");
      problem.setProperty("reason", "captureDeclined");
      problem.setProperty("declineReason", result.declineReason());
      return ResponseEntity.of(problem).build();
    }
    return ResponseEntity.ok(PaymentResponse.of(payment));
  }

  /**
   * 200 with the Payment the Refund left, {@code PARTIALLY_REFUNDED} or {@code REFUNDED}; 402
   * {@code refundDeclined} with the gateway's {@code declineReason} when the gateway refuses, the
   * refusal recorded and the Payment as it was; 409 {@code paymentNotRefundable} unless the Payment
   * is captured or partly refunded; 422 {@code refundExceedsCaptured} for more than is left to
   * refund, or another Currency, with nothing asked of the gateway; 404 for an unknown Payment or
   * another Customer's; 502 when the gateway fails to answer, with nothing recorded. The {@code
   * Idempotency-Key} is required, and is passed on to the gateway; a repeat replays the first
   * response and refunds nothing more.
   */
  @PostMapping("/{id}/refunds")
  @PreAuthorize("hasRole('ORCHESTRATION')")
  @IdempotentCommand
  ResponseEntity<?> refund(
      @PathVariable String id,
      // Never null here: @IdempotentCommand refuses a request without one.
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody RefundPaymentRequest request) {
    var customerId = request.customer();
    var amount = request.money();
    var refundFor = request.refundFor();
    var result =
        parse(id)
            .flatMap(
                paymentId ->
                    refund.refund(customerId, paymentId, amount, refundFor, idempotencyKey))
            .orElseThrow(() -> new PaymentNotFoundException(id));
    var payment = result.payment();
    log.info(
        "Payment {} for Order {} is {} after a Refund for {}",
        payment.id(),
        payment.orderId(),
        payment.status(),
        refundFor);
    if (result.outcome() == RefundOutcome.DECLINED) {
      var problem =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.PAYMENT_REQUIRED,
              "The gateway declined to refund Payment " + payment.id() + "; it is as it was.");
      problem.setProperty("reason", "refundDeclined");
      problem.setProperty("declineReason", result.declineReason());
      return ResponseEntity.of(problem).build();
    }
    return ResponseEntity.ok(PaymentResponse.of(payment));
  }

  /**
   * 200 with the voided Payment, whether this void released it or an earlier one did; 409 {@code
   * alreadyCaptured} for a captured or refunded Payment and {@code paymentNotVoidable} for a
   * declined one; 404 for an unknown one or another Customer's; 502 when the gateway fails to
   * answer.
   */
  @PostMapping("/{id}/void")
  @PreAuthorize("hasRole('ORCHESTRATION')")
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
   * never reveals that someone else's Payment exists. A {@code customerId} parameter is ignored.
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('CUSTOMER')")
  PaymentResponse payment(CurrentCustomer customer, @PathVariable String id) {
    return ownedPayment(customer.id(), id);
  }

  /**
   * The named Customer's Payment, as the checkout Saga reads it while it awaits settlement; 404 as
   * for the Customer's own read. A path of its own, so it never shadows the Customer's {@code GET
   * /payments/{id}} when a request names a customer.
   */
  @GetMapping("/{id}/for-customer")
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

  /**
   * The named Customer's Payments for one of their Orders, newest first, as the fulfillment Saga
   * reads them to find the Payment to capture; empty for anyone else's. A path of its own, so it
   * never shadows the Customer's {@code GET /payments?orderId=} when a request names a customer.
   */
  @GetMapping("/by-order")
  @PreAuthorize("hasRole('ORCHESTRATION')")
  List<PaymentResponse> customersPayments(
      @RequestParam String orderId, @RequestParam String customerId) {
    return find.payments(customerId, orderId).stream().map(PaymentResponse::of).toList();
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
    problem.setProperty(
        "reason", e.status().wasCaptured() ? "alreadyCaptured" : "paymentNotVoidable");
    return problem;
  }

  @ExceptionHandler(PaymentNotCapturableException.class)
  ProblemDetail notCapturable(PaymentNotCapturableException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("reason", "paymentNotCapturable");
    return problem;
  }

  @ExceptionHandler(PaymentNotRefundableException.class)
  ProblemDetail notRefundable(PaymentNotRefundableException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("reason", "paymentNotRefundable");
    return problem;
  }

  @ExceptionHandler(RefundExceedsCapturedException.class)
  ProblemDetail refundExceedsCaptured(RefundExceedsCapturedException e) {
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    problem.setProperty("reason", "refundExceedsCaptured");
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
