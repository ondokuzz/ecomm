package com.ecomm.orchestration.application.port.in;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingAddress;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.ShippingOption;
import com.ecomm.orchestration.application.port.in.FulfillmentRequest.Line;
import java.util.List;

/** What Orchestration's consumers do with the Order and Shipment events they read. */
public interface FulfillmentUseCase {

  /**
   * An Order event: the Order as it now stands, and the Correlation ID the event carried. Its
   * Shipping address and option are null on an Order placed before Sprint 5.
   */
  record OrderChanged(
      String orderId,
      String customerId,
      String status,
      List<Line> lines,
      ShippingAddress shippingAddress,
      ShippingOption shippingOption,
      String correlationId) {}

  /**
   * Starts the Order's fulfillment once it is {@code PAID} with a Shipping address; at most once
   * per Order, however often its events are delivered. Any other Order starts nothing.
   */
  void orderChanged(OrderChanged order);

  /**
   * Wakes the Order's fulfillment with its Shipment's change. With no fulfillment running for the
   * Order, it is skipped.
   */
  void shipmentChanged(String orderId, ShipmentChange change);
}
