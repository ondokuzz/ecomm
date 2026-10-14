package com.ecomm.fulfillmentshipping.adapter.in.web;

import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShippingAddress;
import com.ecomm.fulfillmentshipping.domain.ShippingOption;
import java.time.Instant;
import java.util.List;

/** A Shipment as its readers see it; carrier, tracking and delivery are null until they happen. */
record ShipmentResponse(
    String id,
    String orderId,
    String customerId,
    String warehouseId,
    String status,
    ShippingAddress shippingAddress,
    ShippingOption shippingOption,
    List<Line> lines,
    String carrier,
    String trackingNumber,
    List<Tracking> trackingEvents,
    Instant deliveredAt,
    String holdReason,
    List<History> statusHistory,
    Instant createdAt) {

  record Line(
      String variantId,
      String productName,
      int quantity,
      String serialTracking,
      List<String> serialNumbers) {}

  /** A Tracking event, oldest first. */
  record Tracking(String eventId, String status, Instant occurredAt) {}

  record History(String status, Instant at, String by) {}

  static ShipmentResponse of(Shipment s) {
    return new ShipmentResponse(
        s.id().toString(),
        s.orderId(),
        s.customerId(),
        s.warehouseId(),
        s.status().name(),
        s.shippingAddress(),
        s.shippingOption(),
        s.lines().stream()
            .map(
                l ->
                    new Line(
                        l.variantId(),
                        l.productName(),
                        l.quantity(),
                        l.serialTracking().name(),
                        l.serialNumbers()))
            .toList(),
        s.carrier(),
        s.trackingNumber(),
        s.trackingEvents().stream()
            .map(e -> new Tracking(e.eventId(), e.status().name(), e.occurredAt()))
            .toList(),
        s.deliveredAt(),
        s.holdReason(),
        s.statusHistory().stream()
            .map(h -> new History(h.status().name(), h.at(), h.by()))
            .toList(),
        s.createdAt());
  }
}
