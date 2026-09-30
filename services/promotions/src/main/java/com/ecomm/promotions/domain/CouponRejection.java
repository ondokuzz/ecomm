package com.ecomm.promotions.domain;

/** Why a Coupon doesn't apply to a subtotal, as the {@code reason} a client reads. */
public enum CouponRejection {
  /** No Coupon has the code. */
  UNKNOWN("unknown"),
  /** Staff have switched it off. */
  INACTIVE("inactive"),
  /** Its validity hasn't started. */
  NOT_YET_VALID("notYetValid"),
  /** Its validity has ended. */
  EXPIRED("expired"),
  /** The subtotal is less than its minimum. */
  BELOW_MINIMUM("belowMinimum"),
  /** Its fixed amount or its minimum is in another currency than the subtotal. */
  CURRENCY_MISMATCH("currencyMismatch");

  private final String reason;

  CouponRejection(String reason) {
    this.reason = reason;
  }

  public String reason() {
    return reason;
  }
}
