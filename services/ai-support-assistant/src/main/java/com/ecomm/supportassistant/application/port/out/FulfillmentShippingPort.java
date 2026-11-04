package com.ecomm.supportassistant.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Fulfillment & Shipping's reads of the Customer's own Shipments, and of a Warehouse's Return
 * address, with their own token (ADR 0002).
 */
public interface FulfillmentShippingPort {

  /** The Order's Shipment; not found while it has none, and for another Customer's Order. */
  Lookup<ShipmentView> shipment(String orderId);

  /** Where an RMA's units go back to the Warehouse; not found for one that isn't configured. */
  Lookup<ReturnAddress> returnAddress(String warehouseId);

  /**
   * A Warehouse's Return address, as a parcel is addressed.
   *
   * @param country an ISO 3166-1 alpha-2 code, such as {@code DE}
   */
  record ReturnAddress(String name, String street, String postcode, String city, String country) {}

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
