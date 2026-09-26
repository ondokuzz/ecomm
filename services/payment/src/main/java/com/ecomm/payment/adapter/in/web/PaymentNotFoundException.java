package com.ecomm.payment.adapter.in.web;

/** No Payment has this ID. */
class PaymentNotFoundException extends RuntimeException {

  PaymentNotFoundException(String id) {
    super("No payment " + id);
  }
}
