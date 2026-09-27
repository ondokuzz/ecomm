package com.ecomm.ordermanagement.application.port.out;

import com.ecomm.ordermanagement.domain.Order;
import com.ecomm.ordermanagement.domain.OrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

  void add(Order order);

  Optional<Order> find(UUID id);

  /** Newest first. */
  List<Order> findByCustomer(String customerId);

  /**
   * Sets the Order's status to {@code to} only if it is still {@code from}; false if it wasn't, so
   * two concurrent changes can't both win.
   */
  boolean replaceStatus(UUID id, OrderStatus from, OrderStatus to);
}
