package com.ecomm.notifications;

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
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only: the events Notifications consumes, as Order Management and Catalog publish them. Each
 * is checked against its topic's schema in {@code platform/event-schemas} before it is sent, so a
 * test can't rely on an event no producer could publish.
 */
final class Events {

  static final String ORDERS = "order-management.order";
  static final String PRODUCTS = "catalog.product";
  static final List<String> TOPICS = List.of(ORDERS, PRODUCTS);
  static final List<String> GROUPS = List.of("notifications.orders", "notifications.products");

  private static final JsonMapper JSON = new JsonMapper();
  private static final JsonSchemaFactory SCHEMAS =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

  private Events() {}

  /** A new Order of the Customer's, placed now, with no lines yet. */
  static OrderEvent order(String customerId) {
    return new OrderEvent(UUID.randomUUID().toString(), customerId);
  }

  /** A Product with one Variant per {@code variantIds}, each with no axis values. */
  static ProductEvent product(String sku, String name, String... variantIds) {
    return new ProductEvent(sku, name, List.of(variantIds));
  }

  static final class OrderEvent {
    private final String orderId;
    private final String customerId;
    private final List<Map<String, Object>> lines = new ArrayList<>();
    private final List<Map<String, Object>> history = new ArrayList<>();
    private final Instant placedAt = Instant.parse("2026-10-21T09:30:00Z");
    private String currency = "EUR";
    private long shippingMinor = 499;
    private long version;
    private String eventId;

    private OrderEvent(String orderId, String customerId) {
      this.orderId = orderId;
      this.customerId = customerId;
    }

    String orderId() {
      return orderId;
    }

    /** The Order's reference, as the Storefront shows it. */
    String reference() {
      return "#" + orderId.substring(0, 8).toUpperCase();
    }

    OrderEvent line(String variantId, int quantity, long unitPriceMinor) {
      lines.add(
          Map.of("variantId", variantId, "quantity", quantity, "unitPrice", money(unitPriceMinor)));
      return this;
    }

    OrderEvent currency(String currency) {
      this.currency = currency;
      return this;
    }

    /** Publishes the Order {@code PLACED}, as version 1. */
    OrderEvent placed() {
      return publish("PLACED", "PLACED");
    }

    /** Publishes the Order's change to {@code status}, at the next version. */
    OrderEvent changedTo(String status) {
      return publish("STATUS_CHANGED", status);
    }

    OrderEvent paid() {
      return changedTo("PAID");
    }

    /** Publishes the last event again, the same event ID included: a redelivery. */
    OrderEvent redelivered() {
      send(ORDERS, orderId, lastEvent);
      return this;
    }

    private Map<String, Object> lastEvent;

    private OrderEvent publish(String change, String status) {
      version++;
      eventId = UUID.randomUUID().toString();
      history.add(Map.of("status", status, "at", Instant.now().toString()));
      long subtotal =
          lines.stream()
              .mapToLong(
                  l ->
                      ((Number) ((Map<?, ?>) l.get("unitPrice")).get("amountMinor")).longValue()
                          * ((Number) l.get("quantity")).longValue())
              .sum();
      var order = new LinkedHashMap<String, Object>();
      order.put("customerId", customerId);
      order.put("status", status);
      order.put("placedAt", placedAt.toString());
      order.put("lines", lines);
      order.put("discounts", List.of());
      order.put("tax", money(0));
      order.put("subtotal", money(subtotal));
      order.put("total", money(subtotal + shippingMinor));
      order.put(
          "shippingAddress",
          Map.of(
              "recipientName",
              "Ada Lovelace",
              "line1",
              "1 Main St",
              "postcode",
              "10115",
              "city",
              "Berlin",
              "country",
              "DE"));
      order.put(
          "shippingOption",
          Map.of("id", "STANDARD", "carrier", "DHL", "cost", money(shippingMinor)));
      order.put("statusHistory", List.copyOf(history));
      var event = new LinkedHashMap<String, Object>();
      event.put("eventId", eventId);
      event.put("occurredAt", Instant.now().toString());
      event.put("orderId", orderId);
      event.put("version", version);
      event.put("change", change);
      event.put("order", order);
      lastEvent = event;
      send(ORDERS, orderId, event);
      return this;
    }

    private Map<String, Object> money(long amountMinor) {
      return Map.of("amountMinor", amountMinor, "currency", currency);
    }
  }

  static final class ProductEvent {
    private final String sku;
    private final String name;
    private final List<String> variantIds;
    private final Map<String, Map<String, String>> axisValues = new LinkedHashMap<>();
    private long version = 1;

    private ProductEvent(String sku, String name, List<String> variantIds) {
      this.sku = sku;
      this.name = name;
      this.variantIds = variantIds;
    }

    ProductEvent version(long version) {
      this.version = version;
      return this;
    }

    /** The Variant's value on a Variant axis, such as {@code storage} {@code 128 GB}. */
    ProductEvent axis(String variantId, String axis, String value) {
      axisValues.computeIfAbsent(variantId, v -> new LinkedHashMap<>()).put(axis, value);
      return this;
    }

    void publish() {
      var product = new LinkedHashMap<String, Object>();
      product.put("name", name);
      product.put("category", "phones");
      product.put("attributes", Map.of());
      product.put("images", List.of());
      product.put(
          "variants",
          variantIds.stream()
              .map(
                  id ->
                      Map.of(
                          "variantId",
                          id,
                          "axisValues",
                          axisValues.getOrDefault(id, Map.of()),
                          "price",
                          Map.of("amountMinor", 1000, "currency", "EUR"),
                          "images",
                          List.of()))
              .toList());
      product.put("removed", false);
      var event = new LinkedHashMap<String, Object>();
      event.put("eventId", UUID.randomUUID().toString());
      event.put("occurredAt", Instant.now().toString());
      event.put("sku", sku);
      event.put("version", version);
      event.put("change", version == 1 ? "CREATED" : "UPDATED");
      event.put("product", product);
      send(PRODUCTS, sku, event);
    }
  }

  private static void send(String topic, String key, Map<String, Object> event) {
    var json = JSON.writeValueAsString(event);
    var errors = schemaOf(topic).validate(json, InputFormat.JSON);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(topic + " event breaks its schema: " + errors);
    }
    Topics.send(topic, key, json, null);
  }

  private static JsonSchema schemaOf(String topic) {
    try (var in =
        Events.class.getClassLoader().getResourceAsStream("event-schemas/" + topic + ".json")) {
      return SCHEMAS.getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
