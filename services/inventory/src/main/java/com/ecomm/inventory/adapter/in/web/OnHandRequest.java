package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.domain.InvalidStockRequestException;

/**
 * How many units Staff have on hand: {@code {"onHand"}}. Taken raw, so {@code 1.5} is rejected
 * rather than truncated.
 */
record OnHandRequest(Object onHand) {

  int toOnHand() {
    if (!(onHand instanceof Integer count)) {
      throw new InvalidStockRequestException("onHand must be an integer");
    }
    return count;
  }
}
