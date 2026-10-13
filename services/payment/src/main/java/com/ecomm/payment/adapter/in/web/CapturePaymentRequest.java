package com.ecomm.payment.adapter.in.web;

import com.ecomm.payment.domain.InvalidPaymentException;

/**
 * Names the Customer whose Payment is captured: {@code {"customerId"}}. Taken raw so that a number
 * is rejected rather than coerced.
 */
record CapturePaymentRequest(Object customerId) {

  String customer() {
    if (!(customerId instanceof String id) || id.isBlank()) {
      throw new InvalidPaymentException("a capture needs a customerId");
    }
    return id;
  }
}
