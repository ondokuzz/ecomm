package com.ecomm.promotions.application.port.out;

import com.ecomm.promotions.domain.CouponRedemption;
import com.ecomm.promotions.domain.CouponUsage;
import com.ecomm.promotions.domain.RedemptionHold;
import java.time.Instant;
import java.util.Map;

/**
 * Where Redemption holds and Coupon redemptions live, each under its Checkout Session. A hold is
 * live until its {@code expiresAt}; at {@code now} or after, it counts for nothing.
 */
public interface RedemptionRepository {

  /**
   * The Coupon's redemptions and holds live at {@code now}, but for {@code exceptSession}'s; every
   * session's when it is null.
   */
  CouponUsage usage(String couponCode, Instant now, String exceptSession);

  /** As {@link #usage}, for the Customer {@code customerId}'s alone. */
  CouponUsage usageBy(String couponCode, String customerId, Instant now, String exceptSession);

  /** Every Coupon's use at {@code now}, by code; a Coupon never used is left out. */
  Map<String, CouponUsage> usageOfEvery(Instant now);

  /** Keeps {@code hold} in place of any its session already had. */
  void hold(RedemptionHold hold);

  /** Drops the session's hold, if it has one. */
  void release(String sessionId);

  /**
   * Records {@code redemption}, made {@code at}, and drops its session's hold. A session that has
   * already redeemed a Coupon records nothing more.
   */
  void redeem(CouponRedemption redemption, Instant at);
}
