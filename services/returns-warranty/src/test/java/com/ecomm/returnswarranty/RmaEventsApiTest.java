package com.ecomm.returnswarranty;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.events.EventBackbone;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Each change to an RMA publishes a {@code returns-warranty.rma} event: a snapshot with its
 * version, read from the topic as any consumer would and checked against the topic's schema.
 */
class RmaEventsApiTest extends RmasApiTest {

  private static final JsonMapper JSON = new JsonMapper();
  private static final JsonSchema SCHEMA = Shipments.loadSchema(RMA_TOPIC);

  @Test
  void aRequestAndItsDecisionArePublishedInOrder() {
    var phone = newVariant("PHONE");
    Products.product("Galaxy S25", phone).tracked("IMEI").warranty(24, "Samsung").publish();
    var imei = newImei();
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(phone, "Galaxy S25", 1, "IMEI", imei)
            .delivered(RECENTLY);
    var rma = requested(rma(shipment.orderId(), phone, 1, "WARRANTY_CLAIM", imei));
    reject(STAFF, rma.id(), "{\"reason\": \"Water damage\"}").expectStatus().isOk();

    var events = eventsOf(rma.id(), 2);

    assertThat(events).hasSize(2);
    var requested = events.get(0);
    assertThat(requested.get("rmaId").asString()).isEqualTo(rma.id());
    assertThat(requested.get("version").asLong()).isEqualTo(1);
    assertThat(requested.get("change").asString()).isEqualTo("REQUESTED");
    var snapshot = requested.get("rma");
    assertThat(snapshot.get("reference").asString()).isEqualTo(rma.reference());
    assertThat(snapshot.get("orderId").asString()).isEqualTo(shipment.orderId());
    assertThat(snapshot.get("customerId").asString()).isEqualTo(CUSTOMER_ID);
    assertThat(snapshot.get("variantId").asString()).isEqualTo(phone);
    assertThat(snapshot.get("productName").asString()).isEqualTo("Galaxy S25");
    assertThat(snapshot.get("quantity").asInt()).isEqualTo(1);
    assertThat(snapshot.get("serialNumbers").get(0).asString()).isEqualTo(imei);
    assertThat(snapshot.get("kind").asString()).isEqualTo("WARRANTY_CLAIM");
    assertThat(snapshot.get("status").asString()).isEqualTo("REQUESTED");
    assertThat(snapshot.get("window").get("kind").asString()).isEqualTo("WARRANTY_WINDOW");
    assertThat(snapshot.get("window").get("warranty").get("manufacturer").asString())
        .isEqualTo("Samsung");
    assertThat(snapshot.get("refundAmount").get("amountMinor").asLong()).isEqualTo(1000);
    assertThat(snapshot.get("decisions")).hasSize(1);
    assertThat(snapshot.get("decisions").get(0).get("decidedBy").asString()).isEqualTo("CUSTOMER");

    var rejected = events.get(1);
    assertThat(rejected.get("version").asLong()).isEqualTo(2);
    assertThat(rejected.get("change").asString()).isEqualTo("REJECTED");
    assertThat(rejected.get("rma").get("status").asString()).isEqualTo("REJECTED");
    var decision = rejected.get("rma").get("decisions").get(1);
    assertThat(decision.get("status").asString()).isEqualTo("REJECTED");
    assertThat(decision.get("decidedBy").asString()).isEqualTo("staff-7");
    assertThat(decision.get("note").asString()).isEqualTo("Water damage");
  }

  @Test
  void aRefusedRequestPublishesNothing() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 1, "NONE").delivered(RECENTLY);
    var rma = requested(rma(shipment.orderId(), charger, 1, "RETURN"));
    refused(rma(shipment.orderId(), charger, 1, "RETURN"));
    approve(STAFF, rma.id(), "{}").expectStatus().isOk();
    approve(STAFF, rma.id(), "{}").expectStatus().isEqualTo(409);

    assertThat(eventsOf(rma.id(), 2))
        .extracting(event -> event.get("change").asString())
        .containsExactly("REQUESTED", "APPROVED");
  }

  @Test
  void eachStepToItsResolutionIsPublishedWithWhatItAdded() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 1, "NONE").delivered(RECENTLY);
    var rma =
        inspected(
            received(approved(requested(rma(shipment.orderId(), charger, 1, "RETURN")))),
            "ACCEPTED");
    step(
            ORCHESTRATION,
            rma.id(),
            "resolve",
            "{\"resolution\": \"REFUNDED\", \"paymentTransactionReference\": \"re_7\"}")
        .expectStatus()
        .isOk();

    var events = eventsOf(rma.id(), 5);

    assertThat(events)
        .extracting(event -> event.get("change").asString())
        .containsExactly("REQUESTED", "APPROVED", "RECEIVED", "INSPECTED", "REFUNDED");
    var approved = events.get(1).get("rma");
    assertThat(approved.get("sendBackBy").asString()).isEqualTo("2026-10-29T12:00:00Z");
    assertThat(approved.get("warehouseId").asString()).isEqualTo("WH-BER");
    var refunded = events.get(4).get("rma");
    assertThat(refunded.get("inspection").get("outcome").asString()).isEqualTo("ACCEPTED");
    assertThat(refunded.get("resolution").get("paymentTransactionReference").asString())
        .isEqualTo("re_7");
    assertThat(refunded.get("decisions").get(4).get("decidedBy").asString())
        .isEqualTo("ORCHESTRATION");
  }

  /** The RMA's events, oldest first, once at least {@code atLeast} are there or 20 seconds pass. */
  private static List<JsonNode> eventsOf(String rmaId, int atLeast) {
    var deadline = Instant.now().plus(Duration.ofSeconds(20));
    var records = EventBackbone.recordsKeyed(RMA_TOPIC, rmaId);
    while (records.size() < atLeast && Instant.now().isBefore(deadline)) {
      records = EventBackbone.recordsKeyed(RMA_TOPIC, rmaId);
    }
    return records.stream()
        .map(
            record -> {
              var errors = SCHEMA.validate(record.value(), InputFormat.JSON);
              assertThat(errors).as(record.value()).isEmpty();
              return JSON.readTree(record.value());
            })
        .toList();
  }
}
