package com.ecomm.checkoutpricing.adapter.in.web;

/**
 * The Coupon a Customer applies to their Checkout Session: {@code {"code": "WELCOME10"}}, in any
 * case. Taken raw so that a number or an object is rejected rather than coerced.
 */
record CouponRequest(Object code) {

  /** The longest Coupon code Promotions and an Order hold. */
  static final int MAX_CODE_LENGTH = 64;

  /** The code of {@code request}, which may be missing altogether. */
  static String codeOf(CouponRequest request) {
    if (request == null || request.code() == null) {
      throw new InvalidCouponRequestException("applying a Coupon needs a code");
    }
    if (!(request.code() instanceof String code) || code.isBlank()) {
      throw new InvalidCouponRequestException("code must be a non-blank string");
    }
    if (code.length() > MAX_CODE_LENGTH) {
      throw new InvalidCouponRequestException(
          "a Coupon code is at most " + MAX_CODE_LENGTH + " characters");
    }
    return code.strip();
  }
}
