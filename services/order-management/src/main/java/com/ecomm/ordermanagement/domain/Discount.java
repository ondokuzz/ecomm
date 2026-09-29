package com.ecomm.ordermanagement.domain;

import com.ecomm.commons.money.Money;

/**
 * The amount a Coupon took off an Order, and the code the Customer entered for it. Throws {@link
 * InvalidOrderException} unless both are valid.
 */
public record Discount(String couponCode, Money amount) {

  /** The longest Coupon code a Discount can hold. */
  public static final int MAX_COUPON_CODE_LENGTH = 64;

  public Discount {
    if (couponCode == null || couponCode.isBlank()) {
      throw new InvalidOrderException("a discount needs a couponCode");
    }
    if (couponCode.length() > MAX_COUPON_CODE_LENGTH) {
      throw new InvalidOrderException(
          "a couponCode is at most " + MAX_COUPON_CODE_LENGTH + " characters");
    }
    if (amount == null) {
      throw new InvalidOrderException("a discount needs an amount");
    }
    if (amount.amountMinor() < 0) {
      throw new InvalidOrderException("a discount's amount can't be negative");
    }
  }
}
