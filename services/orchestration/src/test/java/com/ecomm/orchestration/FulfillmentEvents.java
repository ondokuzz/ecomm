package com.ecomm.orchestration;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only: the events Orchestration consumes, as Order Management and Fulfillment & Shipping
 * publish them, with the Correlation ID of the request behind them in the header. Each is checked
 * against its topic's schema in {@code platform/event-schemas} before it is sent, so a test can't
 * rely on an event no producer could publish.
 */
final class FulfillmentEvents {

  static final String ORDERS = "order-management.order";
  static final String SHIPMENTS = "fulfillment-shipping.shipment";
  static final List<String> TOPICS = List.of(ORDERS, SHIPMENTS);
  static final List<String> GROUPS = List.of("orchestration.orders", "orchestration.shipments");

  private static final JsonMapper JSON = new JsonMapper();
  private static final JsonSchemaFactory SCHEMAS =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
  private static final KafkaProducer<String, String> PRODUCER =
      new KafkaProducer<>(
          Map.of("bootstrap.servers", Kafka.bootstrapServers()),
          new StringSerializer(),
          new StringSerializer());

  private FulfillmentEvents() {}

  /** An Order event: the Order {@code orderId}, at {@code version}, in {@code status}. */
  static void order(
      String orderId,
      String customerId,
      long version,
      String status,
      boolean withShipping,
      String correlationId) {
    var eur = Map.of("amountMinor", 79900, "currency", "EUR");
    var order = new LinkedHashMap<String, Object>();
    order.put("customerId", customerId);
    order.put("status", status);
    order.put("placedAt", "2026-10-14T09:00:00Z");
    order.put(
        "lines",
        List.of(
            Map.of("variantId", "PHN-PIXEL-9", "quantity", 1, "unitPrice", eur),
            Map.of(
                "variantId",
                "ACC-CASE",
                "quantity",
                2,
                "unitPrice",
                Map.of("amountMinor", 1999, "currency", "EUR"))));
    order.put("discounts", List.of());
    order.put("tax", Map.of("amountMinor", 0, "currency", "EUR"));
    order.put("subtotal", eur);
    order.put("total", eur);
    if (withShipping) {
      order.put(
          "shippingAddress",
          Map.of(
              "recipientName", "Erika Mustermann",
              "line1", "Hauptstraße 5",
              "postcode", "10115",
              "city", "Berlin",
              "country", "DE"));
      order.put(
          "shippingOption",
          Map.of(
              "id",
              "EXPRESS",
              "carrier",
              "Mock Carrier",
              "cost",
              Map.of("amountMinor", 999, "currency", "EUR")));
    }
    order.put("statusHistory", List.of(Map.of("status", status, "at", "2026-10-14T09:00:00Z")));
    var change = version == 1 ? "PLACED" : "STATUS_CHANGED";
    send(
        ORDERS,
        orderId,
        envelope("orderId", orderId, version, change, "order", order),
        correlationId);
  }

  /**
   * A Shipment event: the Order's Shipment {@code shipmentId}, at {@code version}, in {@code
   * status}.
   */
  static void shipment(
      String shipmentId, String orderId, String customerId, long version, String status) {
    var shipment = new LinkedHashMap<String, Object>();
    shipment.put("orderId", orderId);
    shipment.put("customerId", customerId);
    shipment.put("warehouseId", "WH-BER");
    shipment.put("status", status);
    shipment.put(
        "lines",
        List.of(
            Map.of(
                "variantId",
                "PHN-PIXEL-9",
                "productName",
                "Pixel 9",
                "quantity",
                1,
                "serialTracking",
                "NONE",
                "serialNumbers",
                List.of())));
    shipment.put("trackingEvents", List.of());
    shipment.put(
        "statusHistory",
        List.of(Map.of("status", status, "at", "2026-10-14T09:00:00Z", "by", "ORCHESTRATION")));
    shipment.put("createdAt", "2026-10-14T09:00:00Z");
    var change = "AWAITING_PICK".equals(status) ? "CREATED" : status;
    send(
        SHIPMENTS,
        shipmentId,
        envelope("shipmentId", shipmentId, version, change, "shipment", shipment),
        null);
  }

  private static Map<String, Object> envelope(
      String idName, String id, long version, String change, String stateName, Object state) {
    var event = new LinkedHashMap<String, Object>();
    event.put("eventId", UUID.randomUUID().toString());
    event.put("occurredAt", Instant.now().toString());
    event.put(idName, id);
    event.put("version", version);
    event.put("change", change);
    event.put(stateName, state);
    return event;
  }

  private static void send(
      String topic, String key, Map<String, Object> event, String correlationId) {
    var json = JSON.writeValueAsString(event);
    var errors = schemaOf(topic).validate(json, InputFormat.JSON);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(topic + " event breaks its schema: " + errors);
    }
    var record = new ProducerRecord<>(topic, key, json);
    if (correlationId != null) {
      record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
    }
    try {
      PRODUCER.send(record).get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static JsonSchema schemaOf(String topic) {
    try (var in =
        FulfillmentEvents.class
            .getClassLoader()
            .getResourceAsStream("event-schemas/" + topic + ".json")) {
      return SCHEMAS.getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
