package com.ecomm.reviewsratings;

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
 * Test-only: the events Reviews consumes, as Order Management and Catalog publish them. Each is
 * checked against its topic's schema in {@code platform/event-schemas} before it is sent, so a test
 * can't rely on an event no producer could publish.
 */
final class Events {

  static final String ORDERS = "order-management.order";
  static final String PRODUCTS = "catalog.product";
  static final List<String> TOPICS = List.of(ORDERS, PRODUCTS);

  private static final JsonMapper JSON = new JsonMapper();
  private static final JsonSchemaFactory SCHEMAS =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

  private Events() {}

  /** A new Order by {@code customerId}, placed and not yet paid. */
  static OrderEvent order(String customerId) {
    return new OrderEvent(UUID.randomUUID().toString(), customerId);
  }

  /** A Product with the given Variants. */
  static ProductEvent product(String sku, String... variantIds) {
    return new ProductEvent(sku, List.of(variantIds));
  }

  static final class OrderEvent {
    private final String orderId;
    private final String customerId;
    private long version = 1;
    private String status = "PLACED";
    private final List<String> variantIds = new ArrayList<>();

    private OrderEvent(String orderId, String customerId) {
      this.orderId = orderId;
      this.customerId = customerId;
    }

    OrderEvent line(String variantId) {
      variantIds.add(variantId);
      return this;
    }

    /** The same Order, in {@code status} at {@code version}. */
    OrderEvent at(long version, String status) {
      this.version = version;
      this.status = status;
      return this;
    }

    String id() {
      return orderId;
    }

    void publish() {
      var now = Instant.now().toString();
      var money = Map.of("amountMinor", 1000, "currency", "EUR");
      var order = new LinkedHashMap<String, Object>();
      order.put("customerId", customerId);
      order.put("status", status);
      order.put("placedAt", now);
      order.put(
          "lines",
          variantIds.stream()
              .map(v -> Map.of("variantId", v, "quantity", 1, "unitPrice", money))
              .toList());
      order.put("discounts", List.of());
      order.put("tax", Map.of("amountMinor", 0, "currency", "EUR"));
      order.put("subtotal", money);
      order.put("total", money);
      order.put("statusHistory", List.of(Map.of("status", status, "at", now)));
      var change = version == 1 ? "PLACED" : "STATUS_CHANGED";
      send(ORDERS, orderId, envelope("orderId", orderId, version, change, "order", order));
    }
  }

  static final class ProductEvent {
    private final String sku;
    private final List<String> variantIds;
    private long version = 1;
    private boolean removed;

    private ProductEvent(String sku, List<String> variantIds) {
      this.sku = sku;
      this.variantIds = variantIds;
    }

    ProductEvent version(long version) {
      this.version = version;
      return this;
    }

    ProductEvent removed() {
      this.removed = true;
      return this;
    }

    void publish() {
      var product = new LinkedHashMap<String, Object>();
      product.put("name", sku);
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
                          Map.of(),
                          "price",
                          Map.of("amountMinor", 1000, "currency", "EUR"),
                          "images",
                          List.of()))
              .toList());
      product.put("removed", removed);
      var change = removed ? "REMOVED" : version == 1 ? "CREATED" : "UPDATED";
      send(PRODUCTS, sku, envelope("sku", sku, version, change, "product", product));
    }
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
