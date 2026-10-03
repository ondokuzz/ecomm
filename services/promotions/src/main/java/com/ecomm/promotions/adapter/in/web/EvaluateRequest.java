package com.ecomm.promotions.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.promotions.domain.InvalidPromotionException;

/** A Coupon code and the subtotal to evaluate it against: {@code {"couponCode", "subtotal"}}. */
record EvaluateRequest(Object couponCode, Amount subtotal) {

  String toCouponCode() {
    if (!(couponCode instanceof String code) || code.isBlank()) {
      throw new InvalidPromotionException("couponCode", "must be a non-blank string");
    }
    return code;
  }

  Money toSubtotal() {
    if (subtotal == null) {
      throw new InvalidPromotionException("subtotal", "is needed");
    }
    var money = subtotal.toMoney("subtotal");
    if (money.amountMinor() < 0) {
      throw new InvalidPromotionException("subtotal", "can't be negative");
    }
    return money;
  }
}
