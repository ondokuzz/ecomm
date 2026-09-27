package com.ecomm.ordermanagement.application.port.in;

import com.ecomm.ordermanagement.domain.Order;
import com.ecomm.ordermanagement.domain.OrderStatus;
import java.util.Optional;
import java.util.UUID;

public interface ChangeOrderStatusUseCase {

  /**
   * Moves the Customer's Order to {@code next} and returns it; empty unless the Order exists and
   * belongs to this Customer. Throws {@code IllegalStatusTransitionException} when its current
   * status can't reach {@code next}, and {@link ConcurrentStatusChangeException} when another
   * change got there first.
   */
  Optional<Order> changeStatus(String customerId, UUID id, OrderStatus next);
}
