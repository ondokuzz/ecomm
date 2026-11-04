package com.ecomm.supportassistant.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Fulfillment & Shipping's read of the Customer's own Shipments, with their own token (ADR 0002).
 */
public interface FulfillmentShippingPort {

  /** The Order's Shipment; not found while it has none, and for another Customer's Order. */
  Lookup<ShipmentView> shipment(String orderId);

  /**
   * @param carrier null, as is {@code trackingNumber}, until it is booked with the Carrier
   * @param trackingEvents oldest first
   * @param deliveredAt null until it is delivered
   */
  record ShipmentView(
      String orderId,
      String status,
      String carrier,
      String trackingNumber,
      List<TrackingEvent> trackingEvents,
      Instant deliveredAt) {}

  record TrackingEvent(String status, Instant at) {}
}
