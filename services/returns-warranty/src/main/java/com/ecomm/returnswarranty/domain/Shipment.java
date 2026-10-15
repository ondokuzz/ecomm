package com.ecomm.returnswarranty.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What Returns & Warranty keeps of a Fulfillment & Shipping Shipment, at the newest version it has
 * applied: its Order and Customer, its lines and, once delivered, when.
 *
 * @param status Fulfillment & Shipping's Shipment status, kept as text so a new one doesn't break
 *     the projection
 * @param deliveredAt null until it is delivered
 */
public record Shipment(
    UUID id,
    long version,
    String orderId,
    String customerId,
    String status,
    Instant deliveredAt,
    List<DeliveredLine> lines) {

  public static final String DELIVERED = "DELIVERED";

  public Shipment {
    lines = List.copyOf(lines);
  }

  /** What it delivered, once it is delivered. */
  public Optional<Delivery> delivery() {
    return DELIVERED.equals(status) && deliveredAt != null
        ? Optional.of(new Delivery(id, orderId, customerId, deliveredAt, lines))
        : Optional.empty();
  }
}
