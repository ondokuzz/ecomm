package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Asked where an Order is, the mock provider looks up its Shipment too, from Fulfillment & Shipping
 * with the Customer's own token: a shipped Order's Carrier, tracking number and latest Tracking
 * event, a delivered one's day, and an Order with no Shipment yet said so.
 */
class ShipmentApiTest extends SupportAssistantApiTest {

  static final String SHIPPED_HISTORY =
      " It was placed on 3 November 2026, paid on 3 November 2026, packed on 3 November 2026 and"
          + " shipped on 3 November 2026.";

  private void stubOrderByReference(String status, String... history) {
    var order = order(ORDER_ID, status, history);
    stubOrdersByReference("7F1C2A3B", order);
    stubOrder(ORDER_ID, okJson(order));
  }

  @Test
  void aShippedOrdersCarrierTrackingNumberAndLatestTrackingEventAreGiven() {
    stubOrderByReference("SHIPPED", "PLACED", "PAID", "FULFILLED", "SHIPPED");
    stubShipment(ORDER_ID, okJson(shipment(ORDER_ID, "SHIPPED", "MC0123456789", "IN_TRANSIT")));
    var id = started();

    send(id, "Where is my order #7F1C2A3B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Your Order #7F1C2A3B is shipped."
                + SHIPPED_HISTORY
                + " It shipped with Mock Carrier, tracking number MC0123456789; its latest"
                + " Tracking event: in transit on 4 November 2026.")
        .jsonPath("$.references[0].id")
        .isEqualTo(ORDER_ID);

    FULFILLMENT_SHIPPING.verify(
        getRequestedFor(urlPathEqualTo("/shipments"))
            .withQueryParam("orderId", equalTo(ORDER_ID))
            .withHeader("Authorization", equalTo("Bearer " + customerToken()))
            .withHeader("X-Correlation-Id", equalTo("test-correlation-1")));
  }

  @Test
  void aDeliveredOrdersDayIsGiven() {
    stubOrderByReference("DELIVERED", "PLACED", "PAID", "FULFILLED", "SHIPPED", "DELIVERED");
    stubShipment(
        ORDER_ID,
        okJson(
            shipment(
                ORDER_ID,
                "DELIVERED",
                "MC0123456789",
                "IN_TRANSIT",
                "OUT_FOR_DELIVERY",
                "DELIVERED")));
    var id = started();

    send(id, "Has my parcel for #7F1C2A3B arrived?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(
            String.class,
            text ->
                assertThat(text)
                    .endsWith(
                        " It was delivered on 6 November 2026 by Mock Carrier, tracking number"
                            + " MC0123456789."));
  }

  @Test
  void anOrderWithNoShipmentYetIsSaidSo() {
    stubOrderByReference("PAID", "PLACED", "PAID");
    stubShipment(ORDER_ID, notFound());
    var id = started();

    send(id, "Where is my order #7F1C2A3B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Your Order #7F1C2A3B is paid. It was placed on 3 November 2026 and paid on 3 November"
                + " 2026. It has no Shipment yet, so it isn't packed yet.");
  }

  @Test
  void aShipmentNotYetHandedToTheCarrierIsSaidSo() {
    stubOrderByReference("PAID", "PLACED", "PAID");
    stubShipment(ORDER_ID, okJson(shipment(ORDER_ID, "AWAITING_PICK", null)));
    var id = started();

    send(id, "Where is my order #7F1C2A3B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(
            String.class,
            text ->
                assertThat(text)
                    .endsWith(
                        " It hasn't shipped yet: it is waiting to be picked at the warehouse."));
  }

  @Test
  void anOrderNotYetPaidHasNoShipmentToLookUp() {
    stubOrderByReference("PLACED", "PLACED");
    var id = started();

    send(id, "Where is my order #7F1C2A3B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("Your Order #7F1C2A3B is placed. It was placed on 3 November 2026.");

    FULFILLMENT_SHIPPING.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void fulfillmentAndShippingFailingIsSaidSo() {
    stubOrderByReference("SHIPPED", "PLACED", "PAID", "FULFILLED", "SHIPPED");
    stubShipment(ORDER_ID, serviceUnavailable());
    var id = started();

    send(id, "Where is my order #7F1C2A3B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("I couldn't look up your Shipment just now. Please try again in a moment.");
  }
}
