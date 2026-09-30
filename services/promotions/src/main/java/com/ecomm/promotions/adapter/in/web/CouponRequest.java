package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.domain.Coupon;
import com.ecomm.promotions.domain.CouponDiscount;
import com.ecomm.promotions.domain.InvalidCouponException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * A Coupon as Staff send it: {@code {"code", "discount": {"type": "PERCENT_OFF", "percentOff"} or
 * {"type": "AMOUNT_OFF", "amountOff": {"amountMinor", "currency"}}, "minimumSubtotal", "validFrom",
 * "validUntil", "active"}}, with {@code minimumSubtotal} optional. On update the code comes from
 * the path and may be left out here. The values are taken raw so that nothing is coerced.
 */
record CouponRequest(
    Object code,
    DiscountBody discount,
    Amount minimumSubtotal,
    Object validFrom,
    Object validUntil,
    Object active) {

  record DiscountBody(Object type, Object percentOff, Amount amountOff) {

    CouponDiscount toDiscount() {
      if ("PERCENT_OFF".equals(type)) {
        if (!(percentOff instanceof Integer percent)) {
          throw new InvalidCouponException("percentOff must be an integer from 1 to 100");
        }
        return new CouponDiscount.PercentOff(percent);
      }
      if ("AMOUNT_OFF".equals(type)) {
        if (amountOff == null) {
          throw new InvalidCouponException("an AMOUNT_OFF discount needs an amountOff");
        }
        return new CouponDiscount.AmountOff(amountOff.toMoney("amountOff"));
      }
      throw new InvalidCouponException("discount.type must be PERCENT_OFF or AMOUNT_OFF");
    }
  }

  Coupon toCoupon() {
    if (!(code instanceof String text)) {
      throw new InvalidCouponException("code must be a string");
    }
    return toCoupon(text);
  }

  Coupon toCouponWithCode(String pathCode) {
    if (code != null
        && !(code instanceof String text
            && Coupon.normalize(text).equals(Coupon.normalize(pathCode)))) {
      throw new InvalidCouponException("code in the body must match the one in the path");
    }
    return toCoupon(pathCode);
  }

  private Coupon toCoupon(String code) {
    if (discount == null) {
      throw new InvalidCouponException("a Coupon needs a discount");
    }
    if (!(active instanceof Boolean isActive)) {
      throw new InvalidCouponException("active must be true or false");
    }
    return new Coupon(
        code,
        discount.toDiscount(),
        minimumSubtotal == null ? null : minimumSubtotal.toMoney("minimumSubtotal"),
        instant("validFrom", validFrom),
        instant("validUntil", validUntil),
        isActive);
  }

  private static Instant instant(String field, Object value) {
    if (!(value instanceof String text)) {
      throw new InvalidCouponException(field + " must be an ISO 8601 instant");
    }
    try {
      return Instant.parse(text);
    } catch (DateTimeParseException e) {
      throw new InvalidCouponException(
          field + " must be an ISO 8601 instant, such as 2026-01-01T00:00:00Z");
    }
  }
}
