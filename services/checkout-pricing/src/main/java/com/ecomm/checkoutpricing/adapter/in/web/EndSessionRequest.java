package com.ecomm.checkoutpricing.adapter.in.web;

/**
 * Names the Customer whose Checkout Session is ended: {@code {"customerId"}}. Taken raw so that a
 * number is rejected rather than coerced.
 */
record EndSessionRequest(Object customerId) {

  String customer() {
    if (!(customerId instanceof String id) || id.isBlank()) {
      throw new InvalidEndSessionRequestException("ending a session needs a customerId");
    }
    return id;
  }
}
