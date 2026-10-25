package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.application.port.in.CouponView;
import java.time.Instant;

/**
 * A Coupon as Staff read it, with its {@code redemptions}, its live Redemption {@code holds} and
 * its {@code state} now: {@code active}, {@code scheduled}, {@code expired}, {@code off} or {@code
 * exhausted}. {@code minimumSubtotal}, {@code usageLimit} and {@code perCustomerLimit} are null
 * when it has none.
 */
record CouponResponse(
    String code,
    DiscountRuleBody discount,
    Amount minimumSubtotal,
    Instant validFrom,
    Instant validUntil,
    boolean active,
    Integer usageLimit,
    Integer perCustomerLimit,
    int redemptions,
    int holds,
    String state) {

  static CouponResponse of(CouponView view) {
    var coupon = view.coupon();
    return new CouponResponse(
        coupon.code(),
        DiscountRuleBody.of(coupon.discount()),
        Amount.of(coupon.minimumSubtotal()),
        coupon.validFrom(),
        coupon.validUntil(),
        coupon.active(),
        coupon.usageLimit(),
        coupon.perCustomerLimit(),
        view.usage().redemptions(),
        view.usage().holds(),
        view.state().label());
  }
}
