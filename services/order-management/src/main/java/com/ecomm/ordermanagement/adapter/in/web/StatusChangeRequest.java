package com.ecomm.ordermanagement.adapter.in.web;

import com.ecomm.ordermanagement.domain.OrderStatus;

/**
 * The Order Status to move a Customer's Order to: {@code {"customerId", "status": "PAID"}}. The
 * {@code customerId} must own the Order.
 */
record StatusChangeRequest(Object customerId, Object status) {

  String toCustomerId() {
    return CustomerIds.from(customerId);
  }

  OrderStatus toStatus() {
    return OrderStatuses.from(status);
  }
}
