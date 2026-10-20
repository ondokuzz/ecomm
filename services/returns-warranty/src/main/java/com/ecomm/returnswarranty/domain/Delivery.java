package com.ecomm.returnswarranty.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A delivered Shipment, as Returns & Warranty projects it from Fulfillment & Shipping's events:
 * what an Order's Customer received, from which Warehouse, and when. RMAs are checked against it.
 *
 * @param warehouseId null for a Shipment projected before Warehouses were kept, until it is again
 */
public record Delivery(
    UUID shipmentId,
    String orderId,
    String customerId,
    String warehouseId,
    Instant deliveredAt,
    List<DeliveredLine> lines) {

  public Delivery {
    lines = List.copyOf(lines);
  }

  /**
   * Whether every unit it delivered has been refunded, by the Order's {@code REFUNDED} RMAs among
   * {@code rmas}.
   */
  public boolean fullyRefundedBy(Collection<Rma> rmas) {
    return lines.stream()
        .allMatch(
            line ->
                rmas.stream()
                        .filter(
                            rma ->
                                rma.status() == RmaStatus.REFUNDED
                                    && rma.orderId().equals(orderId)
                                    && rma.variantId().equals(line.variantId()))
                        .mapToInt(Rma::quantity)
                        .sum()
                    >= line.quantity());
  }

  /** The line that delivered {@code variantId}, if one did. */
  public Optional<DeliveredLine> lineOf(String variantId) {
    return lines.stream().filter(line -> line.variantId().equals(variantId)).findFirst();
  }
}
