package com.ecomm.returnswarranty.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A delivered Shipment, as Returns & Warranty projects it from Fulfillment & Shipping's events:
 * what an Order's Customer received, and when. RMAs are checked against it.
 */
public record Delivery(
    UUID shipmentId,
    String orderId,
    String customerId,
    Instant deliveredAt,
    List<DeliveredLine> lines) {

  public Delivery {
    lines = List.copyOf(lines);
  }

  /** The line that delivered {@code variantId}, if one did. */
  public Optional<DeliveredLine> lineOf(String variantId) {
    return lines.stream().filter(line -> line.variantId().equals(variantId)).findFirst();
  }
}
