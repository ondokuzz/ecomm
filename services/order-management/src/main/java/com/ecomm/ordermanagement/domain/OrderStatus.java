package com.ecomm.ordermanagement.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * An Order's lifecycle stage. The main path is {@code PLACED → PAID → FULFILLED → SHIPPED →
 * DELIVERED}. An Order can be {@code CANCELLED} until it is fulfilled, and {@code RETURNED} once
 * delivered; both are final.
 */
public enum OrderStatus {
  PLACED,
  PAID,
  FULFILLED,
  SHIPPED,
  DELIVERED,
  CANCELLED,
  RETURNED;

  /** Whether an Order in this status may move to {@code next}. */
  public boolean canBecome(OrderStatus next) {
    return successors().contains(next);
  }

  private Set<OrderStatus> successors() {
    return switch (this) {
      case PLACED -> EnumSet.of(PAID, CANCELLED);
      case PAID -> EnumSet.of(FULFILLED, CANCELLED);
      case FULFILLED -> EnumSet.of(SHIPPED);
      case SHIPPED -> EnumSet.of(DELIVERED);
      case DELIVERED -> EnumSet.of(RETURNED);
      case CANCELLED, RETURNED -> EnumSet.noneOf(OrderStatus.class);
    };
  }
}
