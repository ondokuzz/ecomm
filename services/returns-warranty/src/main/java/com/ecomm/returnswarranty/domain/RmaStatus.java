package com.ecomm.returnswarranty.domain;

import java.util.Set;

/**
 * Where an RMA is. {@code REQUESTED} by the Customer, it is {@code APPROVED} or {@code REJECTED} by
 * Staff's RMA decision. An approved RMA is {@code RECEIVED} when Staff receive its units, or {@code
 * EXPIRED} when its Send-back deadline passes first; a received one is {@code INSPECTED}; and an
 * inspected one is resolved, {@code REFUNDED}, {@code EXCHANGED} or {@code CLOSED}, or put {@code
 * ON_HOLD}. Rejected, expired, refunded, exchanged and closed RMAs are final, and nothing resolves
 * a hold yet.
 */
public enum RmaStatus {
  REQUESTED,
  APPROVED,
  REJECTED,
  RECEIVED,
  EXPIRED,
  INSPECTED,
  REFUNDED,
  EXCHANGED,
  CLOSED,
  ON_HOLD;

  /** Whether an RMA may move from this status to {@code next}. */
  public boolean canMoveTo(RmaStatus next) {
    return switch (this) {
      case REQUESTED -> next == APPROVED || next == REJECTED;
      case APPROVED -> next == RECEIVED || next == EXPIRED;
      case RECEIVED -> next == INSPECTED;
      case INSPECTED -> Set.of(REFUNDED, EXCHANGED, CLOSED, ON_HOLD).contains(next);
      case REJECTED, EXPIRED, REFUNDED, EXCHANGED, CLOSED, ON_HOLD -> false;
    };
  }

  /**
   * Whether an RMA in this status holds its units against other RMAs: until it is rejected, expires
   * or is closed. A closed return's unit may still be claimed under warranty.
   */
  public boolean holdsUnits() {
    return this != REJECTED && this != EXPIRED && this != CLOSED;
  }
}
