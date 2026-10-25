package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.application.port.in.RedeemCouponsUseCase;
import com.ecomm.promotions.domain.CouponNotApplicableException;
import com.ecomm.promotions.domain.CouponRedemption;
import com.ecomm.promotions.domain.InvalidPromotionException;
import com.ecomm.promotions.domain.RedemptionHold;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout holds and releases a Coupon's redemption for a Checkout Session with its own {@code
 * CHECKOUT} token, and the checkout Saga redeems it with Orchestration's {@code ORCHESTRATION}
 * token. Both are internal: the gateway never routes them.
 */
@RestController
@RequestMapping("/redemptions")
class RedemptionController {

  private static final Logger log = LoggerFactory.getLogger(RedemptionController.class);

  private final RedeemCouponsUseCase redemptions;

  RedemptionController(RedeemCouponsUseCase redemptions) {
    this.redemptions = redemptions;
  }

  /**
   * A Checkout Session's hold, as Checkout sends it: {@code {"sessionId", "customerId",
   * "couponCode", "expiresAt"}}, {@code expiresAt} being the session's own.
   */
  record HoldRequest(Object sessionId, Object customerId, Object couponCode, Object expiresAt) {

    RedemptionHold toHold() {
      return new RedemptionHold(
          Fields.string("sessionId", sessionId),
          Fields.string("customerId", customerId),
          Fields.string("couponCode", couponCode),
          Fields.instant("expiresAt", expiresAt));
    }
  }

  /** A paid Checkout Session's redemption: {@code {"sessionId", "customerId", "couponCode"}}. */
  record RedeemRequest(Object sessionId, Object customerId, Object couponCode) {

    CouponRedemption toRedemption() {
      return new CouponRedemption(
          Fields.string("sessionId", sessionId),
          Fields.string("customerId", customerId),
          Fields.string("couponCode", couponCode));
    }
  }

  /**
   * 204 once the session holds the Coupon, in place of any it held; 422 with a {@code reason},
   * {@code unknown}, {@code exhausted} or {@code alreadyRedeemed}, leaving its hold as it was.
   */
  @PostMapping("/holds")
  @PreAuthorize("hasRole('CHECKOUT')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void hold(@RequestBody HoldRequest request) {
    var hold = request.toHold();
    redemptions.hold(hold);
    log.info(
        "Checkout Session {} holds Coupon {} until {}",
        hold.sessionId(),
        hold.couponCode(),
        hold.expiresAt());
  }

  /** 204 whether or not the session held a Coupon. */
  @DeleteMapping("/holds/{sessionId}")
  @PreAuthorize("hasRole('CHECKOUT')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void release(@PathVariable String sessionId) {
    redemptions.release(sessionId);
    log.info("Released Checkout Session {}'s hold, if any", sessionId);
  }

  /**
   * 204 once the session's Coupon is redeemed, with or without a hold, past its limits, window or
   * switch if need be, and for a Coupon since deleted; the same session again changes nothing.
   */
  @PostMapping
  @PreAuthorize("hasRole('ORCHESTRATION')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void redeem(@RequestBody RedeemRequest request) {
    var redemption = request.toRedemption();
    redemptions.redeem(redemption);
    log.info(
        "Checkout Session {} redeemed Coupon {}", redemption.sessionId(), redemption.couponCode());
  }

  @ExceptionHandler(CouponNotApplicableException.class)
  ProblemDetail refused(CouponNotApplicableException e) {
    log.info("Coupon hold refused: {}", e.rejection().reason());
    return Problems.notApplicable(e);
  }

  @ExceptionHandler(InvalidPromotionException.class)
  ProblemDetail invalid(InvalidPromotionException e) {
    return Problems.invalid(e);
  }
}
