package com.ecomm.ordermanagement.application.port.in;

import com.ecomm.ordermanagement.domain.Order;
import java.util.Optional;
import java.util.UUID;

public interface FindOrdersUseCase {

  /** Empty unless the Order exists and belongs to this Customer. */
  Optional<Order> order(String customerId, UUID id);

  /** A page of the Customer's Orders, newest first, {@code size} to a page. */
  Page<Order> orders(String customerId, int page, int size);
}
