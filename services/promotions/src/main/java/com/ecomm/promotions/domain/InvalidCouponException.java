package com.ecomm.promotions.domain;

/** A Coupon was sent with missing or malformed data. */
public class InvalidCouponException extends RuntimeException {

  public InvalidCouponException(String message) {
    super(message);
  }
}
