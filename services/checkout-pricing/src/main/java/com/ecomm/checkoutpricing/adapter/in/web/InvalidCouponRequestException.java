package com.ecomm.checkoutpricing.adapter.in.web;

/** Applying a Coupon was asked for without a usable code. */
class InvalidCouponRequestException extends RuntimeException {

  InvalidCouponRequestException(String message) {
    super(message);
  }
}
