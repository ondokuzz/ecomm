package com.ecomm.checkoutpricing.adapter.in.web;

/** Paying a Checkout Session was asked for without a usable payment method. */
class InvalidPayRequestException extends RuntimeException {

  InvalidPayRequestException(String message) {
    super(message);
  }
}
