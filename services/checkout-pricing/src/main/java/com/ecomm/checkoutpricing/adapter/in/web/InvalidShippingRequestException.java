package com.ecomm.checkoutpricing.adapter.in.web;

/** A Shipping address or option request without what it needs. */
class InvalidShippingRequestException extends RuntimeException {

  InvalidShippingRequestException(String message) {
    super(message);
  }
}
