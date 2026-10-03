package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.domain.Coupon;
import com.ecomm.promotions.domain.InvalidPromotionException;

/**
 * A Coupon as Staff send it: {@code {"code", "discount", "minimumSubtotal", "validFrom",
 * "validUntil", "active"}}, with {@code minimumSubtotal} optional. On update the code comes from
 * the path and may be left out here. The values are taken raw so that nothing is coerced.
 */
record CouponRequest(
    Object code,
    DiscountRuleBody discount,
    Amount minimumSubtotal,
    Object validFrom,
    Object validUntil,
    Object active) {

  Coupon toCoupon() {
    if (!(code instanceof String text)) {
      throw new InvalidPromotionException("code", "must be a string");
    }
    return toCoupon(text);
  }

  Coupon toCouponWithCode(String pathCode) {
    if (code != null
        && !(code instanceof String text
            && Coupon.normalize(text).equals(Coupon.normalize(pathCode)))) {
      throw new InvalidPromotionException("code", "must match the one in the path");
    }
    return toCoupon(pathCode);
  }

  private Coupon toCoupon(String code) {
    var rule = DiscountRuleBody.toRule(discount);
    var minimum = minimumSubtotal == null ? null : minimumSubtotal.toMoney("minimumSubtotal");
    var from = Fields.instant("validFrom", validFrom);
    var until = Fields.instant("validUntil", validUntil);
    return new Coupon(code, rule, minimum, from, until, Fields.bool("active", active));
  }
}
