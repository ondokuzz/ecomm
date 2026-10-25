package com.ecomm.promotions.domain;

import java.time.Instant;

/**
 * A Checkout Session's claim on one use of a Coupon, until {@code expiresAt}, the session's own
 * expiry. While it is live it counts against the Coupon's Usage limits as a redemption would.
 * Throws {@link InvalidPromotionException}, naming the field, unless every field is given.
 */
public record RedemptionHold(
    String sessionId, String customerId, String couponCode, Instant expiresAt) {

  public RedemptionHold {
    sessionId = Redemptions.required("sessionId", sessionId);
    customerId = Redemptions.required("customerId", customerId);
    couponCode = Coupon.normalize(Redemptions.required("couponCode", couponCode));
    if (expiresAt == null) {
      throw new InvalidPromotionException("expiresAt", "is needed");
    }
  }
}
