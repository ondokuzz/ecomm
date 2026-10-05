package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.ordermanagement.domain.InvalidOrderException;
import com.ecomm.ordermanagement.domain.OrderStatus;
import java.util.Arrays;

/** Reads an Order Status a request names. */
final class OrderStatuses {

  private OrderStatuses() {}

  /** The Order Status by its exact name; throws {@link InvalidOrderException} for anything else. */
  static OrderStatus from(Object status) {
    return Arrays.stream(OrderStatus.values())
        .filter(s -> s.name().equals(status))
        .findFirst()
        .orElseThrow(
            () ->
                new InvalidOrderException(
                    "status must be one of " + Arrays.toString(OrderStatus.values())));
  }
}
