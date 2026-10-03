package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.domain.Coupon;
import java.time.Instant;

/** A Coupon as Staff read it; {@code minimumSubtotal} is null when it has none. */
record CouponResponse(
    String code,
    DiscountRuleBody discount,
    Amount minimumSubtotal,
    Instant validFrom,
    Instant validUntil,
    boolean active) {

  static CouponResponse of(Coupon coupon) {
    return new CouponResponse(
        coupon.code(),
        DiscountRuleBody.of(coupon.discount()),
        Amount.of(coupon.minimumSubtotal()),
        coupon.validFrom(),
        coupon.validUntil(),
        coupon.active());
  }
}
