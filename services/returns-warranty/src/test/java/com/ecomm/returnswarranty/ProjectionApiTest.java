package com.ecomm.returnswarranty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.commons.events.EventBackbone;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

/**
 * Fulfillment & Shipping's Shipments and Catalog's Products reach RMAs through the projections: the
 * newest version of each, whichever topic is read first. A stale event changes nothing, and one
 * that can't be read is dead-lettered.
 */
class ProjectionApiTest extends RmasApiTest {

  @Test
  void aStaleShipmentEventDoesntUndoItsDelivery() {
    var charger = newVariant("CHARGER");
    var shipment = Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 2, "NONE");
    shipment.at(1, "AWAITING_PICK").delivered(RECENTLY).at(3, "PACKED");
    awaitShipmentsProjected();

    assertThat(requested(rma(shipment.orderId(), charger, 1, "RETURN")).status())
        .isEqualTo("REQUESTED");
  }

  @Test
  void aShipmentIsClaimableOnlyOnceItsDeliveryArrives() {
    var charger = newVariant("CHARGER");
    var shipment = Shipments.shipment(CUSTOMER_ID).line(charger, "Charger", 1, "NONE");
    shipment.at(5, "SHIPPED");
    awaitShipmentsProjected();
    request(CUSTOMER, rma(shipment.orderId(), charger, 1, "RETURN")).expectStatus().isNotFound();

    shipment.delivered(RECENTLY);

    assertThat(requested(rma(shipment.orderId(), charger, 1, "RETURN")).status())
        .isEqualTo("REQUESTED");
  }

  @Test
  void aProductArrivingAfterItsShipmentIsClaimedAgainst() {
    var phone = newVariant("PHONE");
    var imei = newImei();
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(phone, "Galaxy S25", 1, "IMEI", imei)
            .delivered(RECENTLY);
    awaitShipmentsProjected();
    request(CUSTOMER, rma(shipment.orderId(), phone, 1, "WARRANTY_CLAIM", imei))
        .expectStatus()
        .isEqualTo(503);

    Products.product("Galaxy S25", phone).tracked("IMEI").warranty(24, "Samsung").publish();

    var rma = requested(rma(shipment.orderId(), phone, 1, "WARRANTY_CLAIM", imei));
    assertThat(rma.window().warranty()).isEqualTo(Map.of("months", 24, "manufacturer", "Samsung"));
  }

  @Test
  void aStaleProductEventDoesntUndoItsWarrantyTerms() {
    var laptop = newVariant("LAPTOP");
    var product = Products.product("ThinkPad X1", laptop).warranty(36, "Lenovo").version(3);
    product.publish();
    product.noWarranty().version(2).publish();
    awaitProductsProjected();
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(laptop, "ThinkPad X1", 1, "SERIAL", "SN-LAP1")
            .delivered(RECENTLY);

    var rma = requested(rma(shipment.orderId(), laptop, 1, "WARRANTY_CLAIM", "SN-LAP1"));

    assertThat(rma.window().warranty()).isEqualTo(Map.of("months", 36, "manufacturer", "Lenovo"));
  }

  @Test
  void anUnreadableShipmentEventIsDeadLettered() {
    var key = UUID.randomUUID().toString();
    // No shipment: a required field.
    sendRaw(
        Shipments.TOPIC,
        key,
        """
        {"eventId": "%s", "occurredAt": "2026-10-15T12:00:00Z", "shipmentId": "%s",
         "version": 1, "change": "CREATED"}
        """
            .formatted(UUID.randomUUID(), key));

    await()
        .atMost(Duration.ofSeconds(60))
        .until(() -> !EventBackbone.recordsKeyed(Shipments.GROUP + ".dlt", key).isEmpty());
  }

  private static void sendRaw(String topic, String key, String json) {
    try (var producer =
        new KafkaProducer<String, String>(
            Map.of("bootstrap.servers", EventBackbone.bootstrapServers()),
            new StringSerializer(),
            new StringSerializer())) {
      producer.send(new ProducerRecord<>(topic, key, json)).get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
