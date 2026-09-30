package com.ecomm.checkoutpricing.domain;

/**
 * Promotions found that a Coupon doesn't apply to the Checkout Session, for {@code reason}: {@code
 * unknown}, {@code inactive}, {@code notYetValid}, {@code expired}, {@code belowMinimum} or {@code
 * currencyMismatch}.
 */
public class CouponNotApplicableException extends RuntimeException {

  private final String reason;

  public CouponNotApplicableException(String reason) {
    super("The Coupon doesn't apply: " + reason);
    this.reason = reason;
  }

  public String reason() {
    return reason;
  }
}
