package com.ecomm.ordermanagement.application.port.in;

import com.ecomm.ordermanagement.domain.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FindOrdersUseCase {

  /** Empty unless the Order exists and belongs to this Customer. */
  Optional<Order> order(String customerId, UUID id);

  /** The Customer's Orders, newest first. */
  List<Order> orders(String customerId);
}
