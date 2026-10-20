package com.ecomm.fulfillmentshipping;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.fulfillmentshipping.adapter.out.carrier.CarrierWebhookDelivery.SignedWebhook;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Once it books a Shipment, the mock carrier sends signed Carrier webhooks to {@code POST
 * /webhooks/carrier}: in transit, out for delivery, then delivered. Each event ID is recorded once,
 * every event is kept in time order whenever it arrives, and only a delivery moves the Shipment on.
 */
class CarrierWebhookApiTest extends ShipmentsApiTest {

  @Test
  void eachTrackingEventIsRecordedAndDeliveryDeliversTheShipment() {
    var shipped = shipped();
    var webhooks = carrierWebhooks.about(shipped.trackingNumber());

    deliver(webhooks.get(0)).expectStatus().isOk();
    deliver(webhooks.get(1)).expectStatus().isOk();
    var inTransit = read(STAFF, shipped.id());
    assertThat(inTransit.status()).isEqualTo("SHIPPED");
    assertThat(inTransit.trackingEvents())
        .extracting(TrackingEventView::status)
        .containsExactly("IN_TRANSIT", "OUT_FOR_DELIVERY");
    assertThat(inTransit.deliveredAt()).isNull();

    deliver(webhooks.get(2)).expectStatus().isOk();

    var delivered = read(STAFF, shipped.id());
    assertThat(delivered.status()).isEqualTo("DELIVERED");
    var delivery = delivered.trackingEvents().getLast();
    assertThat(delivery.status()).isEqualTo("DELIVERED");
    assertThat(delivered.deliveredAt()).isEqualTo(delivery.occurredAt());
    assertThat(delivered.statusHistory().getLast())
        .isEqualTo(new HistoryView("DELIVERED", delivery.occurredAt(), "CARRIER"));
  }

  @Test
  void aWebhookDeliveredTwiceIsRecordedOnce() {
    var shipped = shipped();
    var inTransit = carrierWebhooks.about(shipped.trackingNumber()).getFirst();

    deliver(inTransit).expectStatus().isOk();
    var once = read(STAFF, shipped.id());
    deliver(inTransit).expectStatus().isOk();

    assertThat(read(STAFF, shipped.id())).isEqualTo(once);
    assertThat(once.trackingEvents()).hasSize(1);
  }

  @Test
  void aLateEventIsKeptInTimeOrderButDoesNotTakeTheStatusBack() {
    var shipped = shipped();
    var webhooks = carrierWebhooks.about(shipped.trackingNumber());

    deliver(webhooks.get(2)).expectStatus().isOk();
    deliver(webhooks.get(0)).expectStatus().isOk();
    deliver(webhooks.get(1)).expectStatus().isOk();

    var delivered = read(STAFF, shipped.id());
    assertThat(delivered.status()).isEqualTo("DELIVERED");
    assertThat(delivered.trackingEvents())
        .extracting(TrackingEventView::status)
        .containsExactly("IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED");
    assertThat(delivered.statusHistory())
        .extracting(HistoryView::status)
        .containsExactly("AWAITING_PICK", "PICKED", "PACKED", "SHIPPED", "DELIVERED");
  }

  @Test
  void aCustomerSeesTheTrackingEventsOfTheirShipment() {
    var shipped = shipped();
    carrierWebhooks.about(shipped.trackingNumber()).forEach(w -> deliver(w).expectStatus().isOk());

    var mine =
        http.get()
            .uri("/shipments?orderId={orderId}", shipped.orderId())
            .headers(h -> h.setBearerAuth(CUSTOMER))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ShipmentView.class)
            .returnResult()
            .getResponseBody();

    assertThat(mine.status()).isEqualTo("DELIVERED");
    assertThat(mine.trackingNumber()).isEqualTo(shipped.trackingNumber());
    assertThat(mine.trackingEvents()).hasSize(3);
  }

  @Test
  void anUnknownTrackingNumberIsNotFoundSoTheCarrierSendsItAgain() {
    deliver(
            signed(
                """
                {"eventId": "trk-unknown", "trackingNumber": "MC-NOBODY", "status": "IN_TRANSIT",
                 "occurredAt": "2026-10-13T10:00:00Z"}
                """))
        .expectStatus()
        .isNotFound();
  }

  @Test
  void aWrongSignatureIsUnauthorizedAndChangesNothing() {
    var shipped = shipped();
    var webhook = carrierWebhooks.about(shipped.trackingNumber()).getLast();

    deliver(new SignedWebhook(webhook.body(), "sha256=" + "0".repeat(64)))
        .expectStatus()
        .isUnauthorized();
    deliver(new SignedWebhook(webhook.body(), null)).expectStatus().isUnauthorized();
    var other = carrierWebhooks.about(shipped.trackingNumber()).getFirst();
    deliver(new SignedWebhook(webhook.body(), other.signature())).expectStatus().isUnauthorized();

    assertThat(read(STAFF, shipped.id())).isEqualTo(shipped);
  }

  @Test
  void aMalformedWebhookIsABadRequest() {
    for (var body :
        List.of(
            "not json",
            "{\"eventId\": \"e\", \"trackingNumber\": \"MC1\", \"status\": \"LOST\","
                + " \"occurredAt\": \"2026-10-13T10:00:00Z\"}",
            "{\"eventId\": \"e\", \"trackingNumber\": \"MC1\", \"status\": \"DELIVERED\","
                + " \"occurredAt\": \"yesterday\"}")) {
      deliver(signed(body)).expectStatus().isBadRequest();
    }
  }

  private static SignedWebhook signed(String body) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(CARRIER_WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return new SignedWebhook(
          body,
          "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8))));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
