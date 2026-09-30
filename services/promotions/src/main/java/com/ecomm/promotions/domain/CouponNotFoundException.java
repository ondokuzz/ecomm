package com.ecomm.promotions.domain;

/** No Coupon has this code. */
public class CouponNotFoundException extends RuntimeException {

  public CouponNotFoundException(String code) {
    super("No Coupon " + code);
  }
}
