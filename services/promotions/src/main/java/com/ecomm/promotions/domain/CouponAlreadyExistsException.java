package com.ecomm.promotions.domain;

/** A Coupon already has this code. */
public class CouponAlreadyExistsException extends RuntimeException {

  public CouponAlreadyExistsException(String code) {
    super("A Coupon " + code + " already exists");
  }
}
