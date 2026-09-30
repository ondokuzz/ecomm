package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.domain.Discount;

/** What a Coupon takes off, under its upper-case code: {@code {"couponCode", "discount"}}. */
record DiscountResponse(String couponCode, Amount discount) {

  static DiscountResponse of(Discount discount) {
    return new DiscountResponse(discount.couponCode(), Amount.of(discount.amount()));
  }
}
