package com.ecomm.supportassistant.adapter.out.http;

import com.ecomm.supportassistant.application.port.out.FulfillmentShippingPort;
import com.ecomm.supportassistant.application.port.out.Lookup;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fulfillment & Shipping's Customer read, {@code GET /shipments?orderId=}, with the Customer's own
 * token: it answers 404 for an Order with no Shipment, and for another Customer's.
 */
@Component
class FulfillmentShippingClient implements FulfillmentShippingPort {

  private final RestClient http;

  FulfillmentShippingClient(@Qualifier("fulfillmentShippingRestClient") RestClient http) {
    this.http = http;
  }

  private record ShipmentBody(
      String orderId,
      String status,
      String carrier,
      String trackingNumber,
      List<TrackingBody> trackingEvents,
      Instant deliveredAt) {

    ShipmentView toView() {
      return new ShipmentView(
          orderId,
          status,
          carrier,
          trackingNumber,
          trackingEvents == null
              ? List.of()
              : trackingEvents.stream()
                  .map(e -> new TrackingEvent(e.status(), e.occurredAt()))
                  .toList(),
          deliveredAt);
    }
  }

  private record TrackingBody(String status, Instant occurredAt) {}

  @Override
  public Lookup<ShipmentView> shipment(String orderId) {
    return ContextReads.lookup(
        "Fulfillment & Shipping",
        http.get().uri("/shipments?orderId={orderId}", orderId),
        new ParameterizedTypeReference<ShipmentBody>() {},
        ShipmentBody::toView);
  }
}
