package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.domain.InvalidStockRequestException;

/**
 * Returned units to put back on hand, and what they came back on: {@code {"quantity",
 * "reference"}}. {@code quantity} is taken raw, so {@code 1.5} is rejected rather than truncated.
 */
record RestockRequest(Object quantity, Object reference) {

  int toQuantity() {
    if (!(quantity instanceof Integer count)) {
      throw new InvalidStockRequestException("quantity must be an integer");
    }
    return count;
  }

  String toReference() {
    if (reference != null && !(reference instanceof String)) {
      throw new InvalidStockRequestException("reference must be a string");
    }
    return (String) reference;
  }
}
