package com.ecomm.checkoutpricing.adapter.in.web;

/** Ending a Checkout Session was asked for without naming its Customer. */
class InvalidEndSessionRequestException extends RuntimeException {

  InvalidEndSessionRequestException(String message) {
    super(message);
  }
}
