package com.ecomm.returnswarranty;

import com.ecomm.commons.events.EventBackbone;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only: Fulfillment & Shipping's {@code fulfillment-shipping.shipment} events, as it publishes
 * them, each checked against the topic's schema before it is sent.
 */
final class Shipments {

  static final String TOPIC = "fulfillment-shipping.shipment";
  static final String GROUP = "returns-warranty.shipments";

  /** Each unit's Price on the Order a delivered Shipment publishes: 10.00 euros. */
  static final long UNIT_PRICE = 1000;

  private static final JsonMapper JSON = new JsonMapper();
  private static final JsonSchema SCHEMA = loadSchema();

  private Shipments() {}

  /** A Shipment of a new Order of the Customer's, with no lines yet. */
  static Shipment shipment(String customerId) {
    return new Shipment(customerId, UUID.randomUUID().toString(), null);
  }

  /** A replacement Shipment for an RMA on {@code order}'s Order, with no lines yet. */
  static Shipment replacementFor(Shipment order) {
    return new Shipment(order.customerId, order.orderId, UUID.randomUUID().toString());
  }

  static final class Shipment {
    private final String id = UUID.randomUUID().toString();
    private final String orderId;
    private final String customerId;
    private final String rmaId;
    private final List<Map<String, Object>> lines = new ArrayList<>();
    private Orders.Order order;
    private boolean publishesOrder = true;

    private Shipment(String customerId, String orderId, String rmaId) {
      this.customerId = customerId;
      this.orderId = orderId;
      this.rmaId = rmaId;
    }

    /** A line of {@code quantity} units, with a Serial number for each when serial-tracked. */
    Shipment line(
        String variantId,
        String productName,
        int quantity,
        String serialTracking,
        String... serialNumbers) {
      lines.add(
          Map.of(
              "variantId", variantId,
              "productName", productName,
              "quantity", quantity,
              "serialTracking", serialTracking,
              "serialNumbers", List.of(serialNumbers)));
      if (order == null) {
        order = Orders.order(orderId, customerId);
      }
      order.line(variantId, quantity, UNIT_PRICE);
      return this;
    }

    /**
     * Leaves its Order for the test to publish: otherwise, once delivered, its Order Management
     * Order is published first, each unit at {@link #UNIT_PRICE}, without Discounts or Tax.
     */
    Shipment withoutOrder() {
      publishesOrder = false;
      return this;
    }

    String id() {
      return id;
    }

    String orderId() {
      return orderId;
    }

    /** Publishes it delivered at {@code deliveredAt}, as its version 6, after its Order. */
    Shipment delivered(Instant deliveredAt) {
      if (publishesOrder && rmaId == null && order != null) {
        order.publish();
      }
      return publish(6, "DELIVERED", "DELIVERED", deliveredAt);
    }

    /** Publishes it in {@code status}, not delivered, at {@code version}. */
    Shipment at(long version, String status) {
      return publish(version, status, status.equals("AWAITING_PICK") ? "CREATED" : status, null);
    }

    /** Publishes it at {@code version}, delivered at {@code deliveredAt}. */
    Shipment deliveredAt(long version, String change, Instant deliveredAt) {
      return publish(version, "DELIVERED", change, deliveredAt);
    }

    private Shipment publish(long version, String status, String change, Instant deliveredAt) {
      var shipment = new LinkedHashMap<String, Object>();
      if (rmaId != null) {
        shipment.put("kind", "REPLACEMENT");
        shipment.put("rmaId", rmaId);
      }
      shipment.put("orderId", orderId);
      shipment.put("customerId", customerId);
      shipment.put("warehouseId", "WH-BER");
      shipment.put("status", status);
      shipment.put("lines", lines);
      shipment.put("trackingEvents", List.of());
      if (deliveredAt != null) {
        shipment.put("deliveredAt", deliveredAt.toString());
      }
      shipment.put(
          "statusHistory",
          List.of(Map.of("status", "AWAITING_PICK", "at", "2026-01-01T00:00:00Z", "by", "x")));
      shipment.put("createdAt", "2026-01-01T00:00:00Z");
      var event = new LinkedHashMap<String, Object>();
      event.put("eventId", UUID.randomUUID().toString());
      event.put("occurredAt", Instant.now().toString());
      event.put("shipmentId", id);
      event.put("version", version);
      event.put("change", change);
      event.put("shipment", shipment);
      send(TOPIC, SCHEMA, id, JSON.writeValueAsString(event));
      return this;
    }
  }

  static void send(String topic, JsonSchema schema, String key, String json) {
    var errors = schema.validate(json, InputFormat.JSON);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(topic + " event breaks its schema: " + errors);
    }
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

  static JsonSchema loadSchema(String topic) {
    try (var in =
        Shipments.class.getClassLoader().getResourceAsStream("event-schemas/" + topic + ".json")) {
      return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static JsonSchema loadSchema() {
    return loadSchema(TOPIC);
  }
}
