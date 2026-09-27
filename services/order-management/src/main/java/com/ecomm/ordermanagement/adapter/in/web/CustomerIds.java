package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.ordermanagement.domain.InvalidOrderException;
import com.ecomm.ordermanagement.domain.Order;

/** Reads the {@code customerId} Checkout names in a request body. */
final class CustomerIds {

  private CustomerIds() {}

  /**
   * The raw value as a Customer ID. Throws {@link InvalidOrderException} unless it is a string the
   * domain accepts as an Order's owner.
   */
  static String from(Object customerId) {
    if (customerId != null && !(customerId instanceof String)) {
      throw new InvalidOrderException("customerId must be a string");
    }
    Order.requireValidCustomerId((String) customerId);
    return (String) customerId;
  }
}
