package com.ecomm.promotions.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.promotions.domain.InvalidCouponException;

/**
 * {@code Money} as JSON: {@code {"amountMinor", "currency"}}. The values are taken raw so that
 * {@code 1.5} or {@code "7990"} are rejected rather than coerced.
 */
record Amount(Object amountMinor, Object currency) {

  static Amount of(Money money) {
    return money == null
        ? null
        : new Amount(money.amountMinor(), money.currency().getCurrencyCode());
  }

  /**
   * @throws InvalidCouponException naming {@code field} unless this is an integer amount in a known
   *     currency
   */
  Money toMoney(String field) {
    if (!(amountMinor instanceof Integer || amountMinor instanceof Long)) {
      throw new InvalidCouponException(field + ".amountMinor must be an integer in the minor unit");
    }
    if (!(currency instanceof String code)) {
      throw new InvalidCouponException(field + ".currency must be an ISO 4217 code");
    }
    try {
      return Money.of(((Number) amountMinor).longValue(), code);
    } catch (IllegalArgumentException e) {
      throw new InvalidCouponException(field + " has an unknown currency " + code);
    }
  }
}
