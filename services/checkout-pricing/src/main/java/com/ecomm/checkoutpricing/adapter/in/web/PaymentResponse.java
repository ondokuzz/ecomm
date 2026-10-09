package com.ecomm.checkoutpricing.adapter.in.web;

import com.ecomm.checkoutpricing.domain.PaymentAttempt;

/**
 * The latest attempt to pay a Checkout Session: {@code PROCESSING}, {@code PAID}, {@code DECLINED},
 * {@code HOLD_EXPIRED} or {@code FAILED}, with its Order once it has one, and the gateway's reason
 * for a decline.
 */
record PaymentResponse(String status, String orderId, String declineReason) {

  static PaymentResponse of(PaymentAttempt attempt) {
    return new PaymentResponse(attempt.status().name(), attempt.orderId(), attempt.declineReason());
  }
}
