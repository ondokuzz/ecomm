package com.ecomm.fulfillmentshipping.domain;

import java.util.Set;

/**
 * Where a Shipment is:
 *
 * <pre>
 * AWAITING_PICK → PICKED → PACKED → SHIPPED → DELIVERED
 *                           PACKED → ON_HOLD   (its Payment couldn't be captured; final)
 * </pre>
 */
public enum ShipmentStatus {
  AWAITING_PICK,
  PICKED,
  PACKED,
  SHIPPED,
  DELIVERED,
  ON_HOLD;

  /** Whether a Shipment in this status may move to {@code next}. */
  public boolean canMoveTo(ShipmentStatus next) {
    return next().contains(next);
  }

  public boolean isFinal() {
    return next().isEmpty();
  }

  private Set<ShipmentStatus> next() {
    return switch (this) {
      case AWAITING_PICK -> Set.of(PICKED);
      case PICKED -> Set.of(PACKED);
      case PACKED -> Set.of(SHIPPED, ON_HOLD);
      case SHIPPED -> Set.of(DELIVERED);
      case DELIVERED, ON_HOLD -> Set.of();
    };
  }
}
