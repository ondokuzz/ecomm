package com.ecomm.promotions.domain;

/** A Coupon doesn't apply to the subtotal it was evaluated against, for {@code rejection}. */
public class CouponNotApplicableException extends RuntimeException {

  private final CouponRejection rejection;

  public CouponNotApplicableException(String code, CouponRejection rejection) {
    super("Coupon " + code + " doesn't apply: " + rejection.reason());
    this.rejection = rejection;
  }

  public CouponRejection rejection() {
    return rejection;
  }
}
