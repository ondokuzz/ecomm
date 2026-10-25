package com.ecomm.promotions.domain;

/**
 * A paid Checkout Session's use of a Coupon, one per session. It is never given back. Throws {@link
 * InvalidPromotionException}, naming the field, unless every field is given.
 */
public record CouponRedemption(String sessionId, String customerId, String couponCode) {

  public CouponRedemption {
    sessionId = Redemptions.required("sessionId", sessionId);
    customerId = Redemptions.required("customerId", customerId);
    couponCode = Coupon.normalize(Redemptions.required("couponCode", couponCode));
  }
}
