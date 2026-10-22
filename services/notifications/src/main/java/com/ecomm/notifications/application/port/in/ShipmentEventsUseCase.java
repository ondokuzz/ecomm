package com.ecomm.notifications.application.port.in;

import java.time.Instant;
import java.util.List;

/** Records the Notifications a Shipment's event triggers. */
public interface ShipmentEventsUseCase {

  /**
   * A Shipment's event: why it was published, and the Shipment then. {@code kind} is {@code ORDER}
   * or {@code REPLACEMENT}; {@code rmaId}, {@code carrier}, {@code trackingNumber} and {@code
   * deliveredAt} may be {@code null}.
   */
  record ShipmentChanged(
      String eventId,
      Instant occurredAt,
      String change,
      String kind,
      String orderId,
      String rmaId,
      String customerId,
      String carrier,
      String trackingNumber,
      Instant deliveredAt,
      List<Line> lines) {

    public record Line(String productName, int quantity) {}
  }

  /** The Notifications recorded for it: none for a change that triggers nothing, or a repeat. */
  List<String> shipmentChanged(ShipmentChanged event);
}
