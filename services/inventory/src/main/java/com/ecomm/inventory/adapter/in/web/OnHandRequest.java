package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.domain.InvalidStockRequestException;

/**
 * How many units Staff have on hand, and optionally why it changed: {@code {"onHand", "reason"}}.
 * {@code onHand} is taken raw, so {@code 1.5} is rejected rather than truncated.
 */
record OnHandRequest(Object onHand, Object reason) {

  int toOnHand() {
    if (!(onHand instanceof Integer count)) {
      throw new InvalidStockRequestException("onHand must be an integer");
    }
    return count;
  }

  /** {@code null} when none was given. */
  String toReason() {
    if (reason != null && !(reason instanceof String)) {
      throw new InvalidStockRequestException("reason must be a string");
    }
    return (String) reason;
  }
}
