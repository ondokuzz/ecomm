package com.ecomm.fulfillmentshipping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Each change to a Shipment publishes a {@code fulfillment-shipping.shipment} event, a snapshot
 * valid against the topic's schema, one version on.
 */
class ShipmentEventsApiTest extends ShipmentsApiTest {

  @Test
  void eachChangeFromCreationToDeliveryPublishesTheShipment() {
    var shipment = created();
    pick(shipment.id()).expectStatus().isOk();
    var imei = newImei();
    pack(shipment.id(), serials(Map.of(PHONE, List.of(imei)))).expectStatus().isOk();
    var shipped = body(dispatch(shipment.id()).expectStatus().isOk());
    for (var webhook : carrierWebhooks.about(shipped.trackingNumber())) {
      http.post()
          .uri("/webhooks/carrier")
          .header("Carrier-Signature", webhook.signature())
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
          .body(webhook.body())
          .exchange()
          .expectStatus()
          .isOk();
    }

    var events = ShipmentEvents.keyed(shipment.id(), 7);

    assertThat(events)
        .extracting(e -> e.get("change").asString())
        .containsExactly(
            "CREATED", "PICKED", "PACKED", "SHIPPED", "TRACKED", "TRACKED", "DELIVERED");
    assertThat(events)
        .extracting(e -> e.get("version").asLong())
        .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
    var last = events.getLast().get("shipment");
    assertThat(last.get("kind").asString()).isEqualTo("ORDER");
    assertThat(last.get("rmaId")).isNull();
    assertThat(last.get("orderId").asString()).isEqualTo(shipment.orderId());
    assertThat(last.get("customerId").asString()).isEqualTo("customer-42");
    assertThat(last.get("warehouseId").asString()).isEqualTo("WH-BER");
    assertThat(last.get("status").asString()).isEqualTo("DELIVERED");
    assertThat(last.get("carrier").asString()).isEqualTo("Mock Carrier");
    assertThat(last.get("trackingNumber").asString()).isEqualTo(shipped.trackingNumber());
    assertThat(last.get("trackingEvents")).hasSize(3);
    assertThat(last.get("deliveredAt").asString()).isNotBlank();
    assertThat(phoneLine(last).get("serialNumbers").get(0).asString()).isEqualTo(imei);
  }

  @Test
  void aHoldPublishesTheShipmentOnHoldWithItsReason() {
    var packed = packed();
    hold(packed.id(), "authorization_expired").expectStatus().isOk();

    var held = ShipmentEvents.keyed(packed.id(), 4).getLast();

    assertThat(held.get("change").asString()).isEqualTo("ON_HOLD");
    assertThat(held.at("/shipment/holdReason").asString()).isEqualTo("authorization_expired");
    assertThat(held.at("/shipment/carrier").isMissingNode()).isTrue();
  }

  @Test
  void aReplacementIsPublishedWithItsKindAndRma() {
    var replacement = replacement();
    pick(replacement.id()).expectStatus().isOk();

    var events = ShipmentEvents.keyed(replacement.id(), 2);

    assertThat(events)
        .extracting(e -> e.get("change").asString())
        .containsExactly("CREATED", "PICKED");
    assertThat(events)
        .allSatisfy(
            e -> {
              assertThat(e.at("/shipment/kind").asString()).isEqualTo("REPLACEMENT");
              assertThat(e.at("/shipment/rmaId").asString()).isEqualTo(replacement.rmaId());
              assertThat(e.at("/shipment/orderId").asString()).isEqualTo(replacement.orderId());
            });
  }

  @Test
  void aRefusedChangePublishesNothing() {
    var shipment = created();
    dispatch(shipment.id()).expectStatus().isEqualTo(409);

    assertThat(ShipmentEvents.keyed(shipment.id(), 1)).hasSize(1);
  }

  private static JsonNode phoneLine(JsonNode shipment) {
    for (var line : shipment.get("lines")) {
      if (line.get("variantId").asString().equals(PHONE)) {
        return line;
      }
    }
    throw new AssertionError("No phone line");
  }
}
