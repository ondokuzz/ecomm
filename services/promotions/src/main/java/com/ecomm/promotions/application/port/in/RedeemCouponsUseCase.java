package com.ecomm.promotions.application.port.in;

import com.ecomm.promotions.domain.CouponNotApplicableException;
import com.ecomm.promotions.domain.CouponRedemption;
import com.ecomm.promotions.domain.RedemptionHold;

/**
 * Checkout holds a Coupon's redemption for a Checkout Session while it lasts, and the checkout Saga
 * redeems it once the session is paid. A code is matched whatever its case.
 */
public interface RedeemCouponsUseCase {

  /**
   * Holds {@code hold}'s Coupon for its session, in place of any Coupon the session held. A hold
   * the session already has doesn't count against the new one. A refusal leaves the session's hold
   * as it was.
   *
   * @throws CouponNotApplicableException {@code unknown} when there is no such Coupon, {@code
   *     exhausted} or {@code alreadyRedeemed} when its Usage limits are reached
   */
  void hold(RedemptionHold hold);

  /** Releases the session's hold, if it has one. */
  void release(String sessionId);

  /**
   * Turns the session's hold into a redemption, or, with no hold, records the redemption anyway:
   * paying honours the Discount the session was priced with, whatever the Coupon's limits, window
   * or switch say by then, and even if it has since been deleted. A session already redeemed
   * changes nothing.
   */
  void redeem(CouponRedemption redemption);
}
