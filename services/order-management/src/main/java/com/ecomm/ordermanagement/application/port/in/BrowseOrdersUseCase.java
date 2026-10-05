package com.ecomm.ordermanagement.application.port.in;

import com.ecomm.ordermanagement.domain.Order;
import java.util.Optional;
import java.util.UUID;

/** Staff read every Customer's Orders. Nothing here changes one. */
public interface BrowseOrdersUseCase {

  /** The Order, whoever it belongs to; empty if there is none. */
  Optional<Order> anyOrder(UUID id);

  /** A page of the Orders that match, newest first, {@code size} to a page. */
  Page<Order> orders(OrderFilter filter, int page, int size);
}
