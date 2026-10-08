package com.ecomm.payment.adapter.in.web;

import com.ecomm.payment.domain.InvalidPaymentException;

/**
 * Names the Customer whose Payment is voided: {@code {"customerId"}}. Taken raw so that a number is
 * rejected rather than coerced.
 */
record VoidPaymentRequest(Object customerId) {

  String customer() {
    if (!(customerId instanceof String id) || id.isBlank()) {
      throw new InvalidPaymentException("a void needs a customerId");
    }
    return id;
  }
}
