package com.ecomm.ordermanagement.application.port.out;

import com.ecomm.ordermanagement.domain.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

  /** Records a new Order with its lines and its history. */
  void add(Order order);

  Optional<Order> find(UUID id);

  /**
   * Up to {@code limit} of the Customer's Orders, newest first, skipping the first {@code offset}.
   */
  List<Order> findByCustomer(String customerId, long offset, int limit);

  /** How many Orders the Customer has. */
  long countByCustomer(String customerId);

  /**
   * Records the Order's latest Status change: its new Status and version, and the last entry of its
   * history. Only if the stored Order is still at the version before; false if it isn't, so two
   * concurrent changes can't both win.
   */
  boolean recordStatusChange(Order changed);

  /**
   * Up to {@code limit} Orders still to be published as backfilled, locked until the transaction
   * ends; another transaction skips them rather than waiting.
   */
  List<Order> lockAwaitingBackfillEvent(int limit);

  /** Marks these Orders as published as backfilled, so they are never published so again. */
  void markBackfillPublished(List<UUID> ids);
}
