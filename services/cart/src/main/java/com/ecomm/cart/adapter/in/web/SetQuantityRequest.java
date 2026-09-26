package com.ecomm.cart.adapter.in.web;

import com.ecomm.cart.domain.CartItem;
import com.ecomm.cart.domain.InvalidCartItemException;

/**
 * How many of a Variant the Customer wants: {@code {"quantity": 2}}. The quantity is taken as the
 * raw JSON value so that {@code 1.5} or {@code "2"} are rejected rather than coerced to an int.
 */
record SetQuantityRequest(Object quantity) {

  CartItem toItem(String variantId) {
    if (!(quantity instanceof Integer units)) {
      throw new InvalidCartItemException("quantity must be a positive integer");
    }
    return new CartItem(variantId, units);
  }
}
