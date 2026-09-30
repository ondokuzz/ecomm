package com.ecomm.checkoutpricing.adapter.in.web;

import com.ecomm.checkoutpricing.application.port.in.CheckoutUseCase;
import com.ecomm.checkoutpricing.application.port.in.DownstreamFailureException;
import com.ecomm.checkoutpricing.application.port.in.ServiceTokenUnavailableException;
import com.ecomm.checkoutpricing.domain.CheckoutSessionExpiredException;
import com.ecomm.checkoutpricing.domain.CheckoutSessionNotFoundException;
import com.ecomm.checkoutpricing.domain.CouponNotApplicableException;
import com.ecomm.checkoutpricing.domain.EmptyCartException;
import com.ecomm.checkoutpricing.domain.MixedCurrencyException;
import com.ecomm.checkoutpricing.domain.OutOfStockException;
import com.ecomm.checkoutpricing.domain.PaymentDeclinedException;
import com.ecomm.checkoutpricing.domain.UnknownVariantsException;
import com.ecomm.commons.security.CurrentCustomer;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling Customer checks out their own Cart, in two steps: a Checkout Session holds it, and
 * may take a Coupon, then paying the session buys it. Needs a {@code CUSTOMER} token, which
 * Checkout also forwards to Cart.
 */
@RestController
@RequestMapping("/checkout/sessions")
@PreAuthorize("hasRole('CUSTOMER')")
class CheckoutSessionController {

  private static final Logger log = LoggerFactory.getLogger(CheckoutSessionController.class);

  private final CheckoutUseCase checkout;

  CheckoutSessionController(CheckoutUseCase checkout) {
    this.checkout = checkout;
  }

  /** 201 with the new Checkout Session, replacing any the Customer had. */
  @PostMapping
  ResponseEntity<SessionResponse> start(CurrentCustomer customer) {
    var session = checkout.start(customer.id());
    log.info("Started Checkout Session {} until {}", session.id(), session.expiresAt());
    return ResponseEntity.created(URI.create("/checkout/sessions/" + session.id()))
        .body(SessionResponse.of(session));
  }

  /** The Customer's live Checkout Session, or a 404. */
  @GetMapping("/current")
  SessionResponse current(CurrentCustomer customer) {
    return checkout
        .current(customer.id())
        .map(SessionResponse::of)
        .orElseThrow(NoCurrentSessionException::new);
  }

  /**
   * 200 with the session, the Coupon applied in place of any it had; 422 with Promotions' {@code
   * reason} when the Coupon doesn't apply, leaving the session as it was.
   */
  @PutMapping("/{id}/coupon")
  SessionResponse applyCoupon(
      @PathVariable String id,
      CurrentCustomer customer,
      @RequestBody(required = false) CouponRequest request) {
    var session = checkout.applyCoupon(customer.id(), id, CouponRequest.codeOf(request));
    log.info(
        "Applied Coupon {} to Checkout Session {}, taking {} off",
        session.discount().couponCode(),
        id,
        session.discount().amount());
    return SessionResponse.of(session);
  }

  /** 200 with the session, without a Coupon. */
  @DeleteMapping("/{id}/coupon")
  SessionResponse removeCoupon(@PathVariable String id, CurrentCustomer customer) {
    var session = checkout.removeCoupon(customer.id(), id);
    log.info("Removed the Coupon from Checkout Session {}", id);
    return SessionResponse.of(session);
  }

  /**
   * 200 with the paid Order's ID and Order Status; 402 when the payment is declined, 410 once the
   * session has expired. The body is optional here only so that a caller who isn't a Customer gets
   * its 403 before a missing body gets a 400.
   */
  @PostMapping("/{id}/pay")
  CheckoutResponse pay(
      @PathVariable String id,
      CurrentCustomer customer,
      @RequestBody(required = false) PayRequest request) {
    var result = checkout.pay(customer.id(), id, PayRequest.paymentMethodOf(request));
    log.info("Paid Checkout Session {} as Order {}, {}", id, result.orderId(), result.status());
    return CheckoutResponse.of(result);
  }

  @ExceptionHandler(CheckoutSessionNotFoundException.class)
  ProblemDetail sessionNotFound(CheckoutSessionNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(CheckoutSessionExpiredException.class)
  ProblemDetail sessionExpired(CheckoutSessionExpiredException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, e.getMessage());
  }

  @ExceptionHandler(InvalidPayRequestException.class)
  ProblemDetail invalidPayRequest(InvalidPayRequestException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(InvalidCouponRequestException.class)
  ProblemDetail invalidCouponRequest(InvalidCouponRequestException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(CouponNotApplicableException.class)
  ProblemDetail couponNotApplicable(CouponNotApplicableException e) {
    log.info("Coupon rejected: {}", e.reason());
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    problem.setProperty("reason", e.reason());
    return problem;
  }

  @ExceptionHandler(PaymentDeclinedException.class)
  ProblemDetail paymentDeclined(PaymentDeclinedException e) {
    log.info("Payment declined: {}", e.reason());
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, e.getMessage());
    problem.setProperty("declineReason", e.reason());
    return problem;
  }

  @ExceptionHandler(NoCurrentSessionException.class)
  ProblemDetail noCurrentSession(NoCurrentSessionException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(EmptyCartException.class)
  ProblemDetail emptyCart(EmptyCartException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(UnknownVariantsException.class)
  ProblemDetail unknownVariants(UnknownVariantsException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("unknownVariants", e.variantIds());
    return problem;
  }

  @ExceptionHandler(OutOfStockException.class)
  ProblemDetail outOfStock(OutOfStockException e) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setProperty("outOfStock", e.variantIds());
    return problem;
  }

  @ExceptionHandler(MixedCurrencyException.class)
  ProblemDetail mixedCurrency(MixedCurrencyException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(DownstreamFailureException.class)
  ProblemDetail downstreamFailure(DownstreamFailureException e) {
    log.warn("Checkout failed downstream", e);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_GATEWAY, "Checkout could not complete; please try again.");
  }

  @ExceptionHandler(ServiceTokenUnavailableException.class)
  ProblemDetail serviceTokenUnavailable(ServiceTokenUnavailableException e) {
    log.warn("Checkout could not get its service token", e);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE, "Checkout is unavailable right now; please try again.");
  }
}
