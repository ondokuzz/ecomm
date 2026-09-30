package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.domain.Coupon;
import com.ecomm.promotions.domain.CouponDiscount;
import java.time.Instant;

/** A Coupon as Staff read it; {@code minimumSubtotal} is null when it has none. */
record CouponResponse(
    String code,
    DiscountBody discount,
    Amount minimumSubtotal,
    Instant validFrom,
    Instant validUntil,
    boolean active) {

  /** {@code percentOff} for a {@code PERCENT_OFF} discount, {@code amountOff} for the other. */
  record DiscountBody(String type, Integer percentOff, Amount amountOff) {

    static DiscountBody of(CouponDiscount discount) {
      return switch (discount) {
        case CouponDiscount.PercentOff p -> new DiscountBody("PERCENT_OFF", p.percent(), null);
        case CouponDiscount.AmountOff a ->
            new DiscountBody("AMOUNT_OFF", null, Amount.of(a.amount()));
      };
    }
  }

  static CouponResponse of(Coupon coupon) {
    return new CouponResponse(
        coupon.code(),
        DiscountBody.of(coupon.discount()),
        Amount.of(coupon.minimumSubtotal()),
        coupon.validFrom(),
        coupon.validUntil(),
        coupon.active());
  }
}
