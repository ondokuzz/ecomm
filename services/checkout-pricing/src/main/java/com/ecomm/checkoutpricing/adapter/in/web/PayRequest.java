package com.ecomm.checkoutpricing.adapter.in.web;

/**
 * How the Customer pays a Checkout Session: {@code {"paymentMethod": "…"}}, an opaque token the
 * payment gateway issued for their card. Taken raw so that a number or an object is rejected rather
 * than coerced.
 */
record PayRequest(Object paymentMethod) {

  /** The longest payment method Payment accepts. */
  static final int MAX_PAYMENT_METHOD_LENGTH = 255;

  /** The payment method of {@code request}, which may be missing altogether. */
  static String paymentMethodOf(PayRequest request) {
    if (request == null || request.paymentMethod() == null) {
      throw new InvalidPayRequestException("paying needs a paymentMethod");
    }
    if (!(request.paymentMethod() instanceof String method) || method.isBlank()) {
      throw new InvalidPayRequestException("paymentMethod must be a non-blank string");
    }
    if (method.length() > MAX_PAYMENT_METHOD_LENGTH) {
      throw new InvalidPayRequestException(
          "a paymentMethod is at most " + MAX_PAYMENT_METHOD_LENGTH + " characters");
    }
    return method;
  }
}
