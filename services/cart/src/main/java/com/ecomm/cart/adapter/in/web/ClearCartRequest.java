package com.ecomm.cart.adapter.in.web;

/**
 * Names the Customer whose Cart is cleared: {@code {"customerId"}}. Taken raw so that a number is
 * rejected rather than coerced.
 */
record ClearCartRequest(Object customerId) {

  String customer() {
    if (!(customerId instanceof String id) || id.isBlank()) {
      throw new InvalidClearRequestException("a clear needs a customerId");
    }
    return id;
  }
}
