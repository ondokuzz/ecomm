package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.ordermanagement.domain.InvalidOrderException;
import com.ecomm.ordermanagement.domain.OrderStatus;
import java.util.Arrays;

/**
 * The Order Status to move a Customer's Order to: {@code {"customerId", "status": "PAID"}}. The
 * {@code customerId} must own the Order.
 */
record StatusChangeRequest(Object customerId, Object status) {

  String toCustomerId() {
    return CustomerIds.from(customerId);
  }

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
