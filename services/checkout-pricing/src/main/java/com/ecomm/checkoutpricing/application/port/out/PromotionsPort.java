package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.CouponNotApplicableException;
import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.checkoutpricing.domain.PricedLine;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Promotions, which owns Campaigns and Coupons and says what they take off. */
public interface PromotionsPort {

  /**
   * Every Discount {@code lines} are due, in the order they apply: the running Campaigns', then the
   * Coupon {@code couponCode}'s, last, when there is one.
   *
   * @throws CouponNotApplicableException when the Coupon doesn't apply, with Promotions' reason
   */
  List<Discount> evaluate(List<PricedLine> lines, Optional<String> couponCode);

  /**
   * Holds one use of the Coupon {@code couponCode} for the Checkout Session {@code sessionId} until
   * {@code expiresAt}, the session's own expiry, in place of any Coupon the session held.
   *
   * @throws CouponNotApplicableException when it can't be held, with Promotions' reason: {@code
   *     exhausted}, {@code alreadyRedeemed} or {@code unknown}; the session's hold is unchanged
   */
  void hold(String sessionId, String customerId, String couponCode, Instant expiresAt);

  /** Releases the Checkout Session's hold, whether or not it has one. */
  void release(String sessionId);
}
