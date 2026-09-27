package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.ordermanagement.domain.InvalidOrderException;
import com.ecomm.ordermanagement.domain.OrderStatus;
import java.util.Arrays;

/** The Order Status to move an Order to: {@code {"status": "PAID"}}. */
record StatusChangeRequest(Object status) {

  OrderStatus toStatus() {
    return Arrays.stream(OrderStatus.values())
        .filter(s -> s.name().equals(status))
        .findFirst()
        .orElseThrow(
            () ->
                new InvalidOrderException(
                    "status must be one of " + Arrays.toString(OrderStatus.values())));
  }
}
