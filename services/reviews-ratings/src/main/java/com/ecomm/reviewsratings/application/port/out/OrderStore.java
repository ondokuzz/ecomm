package com.ecomm.reviewsratings.application.port.out;

import com.ecomm.reviewsratings.domain.Order;
import java.util.List;
import java.util.Optional;

/** Reviews' copy of every Order Order Management has published. */
public interface OrderStore {

  Optional<Order> find(String orderId);

  void save(Order order);

  /** Every Order the Customer has placed, in any Status. */
  List<Order> ofCustomer(String customerId);
}
