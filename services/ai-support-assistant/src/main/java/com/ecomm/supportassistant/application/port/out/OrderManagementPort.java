package com.ecomm.supportassistant.application.port.out;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.List;

/** Order Management's reads of the Customer's own Orders, made with their own token (ADR 0002). */
public interface OrderManagementPort {

  /** The Customer's latest Orders, newest first, at most {@code limit}. */
  Lookup<List<OrderView>> latestOrders(int limit);

  /**
   * The Customer's Orders with the Order reference, newest first, at most {@code limit}: usually
   * one, and none when no Order of theirs has it.
   *
   * @param reference its eight hex digits, without the {@code #}
   */
  Lookup<List<OrderView>> ordersByReference(String reference, int limit);

  /** One of the Customer's Orders; not found for another Customer's. */
  Lookup<OrderView> order(String orderId);

  record OrderView(
      String id,
      String status,
      Instant placedAt,
      Money total,
      List<Line> lines,
      List<StatusChange> statusHistory) {}

  record Line(String variantId, int quantity, Money unitPrice) {}

  record StatusChange(String status, Instant at) {}
}
