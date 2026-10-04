package com.ecomm.ordermanagement.domain;

import java.time.Instant;

/**
 * One entry in an Order's Order Status history: the Status it moved to, when, and which caller
 * moved it. {@code backfilled} marks an entry reconstructed for an Order placed before histories
 * were kept, whose {@code at} is only its placement time.
 */
public record StatusHistoryEntry(
    OrderStatus status, Instant at, Caller changedBy, boolean backfilled) {

  /** A change as it happens. */
  static StatusHistoryEntry of(OrderStatus status, Instant at, Caller changedBy) {
    return new StatusHistoryEntry(status, at, changedBy, false);
  }
}
