package com.ecomm.recommendations;

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
 * Test-only: the events Recommendations consumes, as Order Management, Catalog and Inventory
 * publish them. Each is checked against its topic's schema in {@code platform/event-schemas} before
 * it is sent, so a test can't rely on an event no producer could publish.
 */
final class Events {

  static final String ORDERS = "order-management.order";
  static final String PRODUCTS = "catalog.product";
  static final String CATEGORIES = "catalog.category";
  static final String STOCK = "inventory.stock";
  static final List<String> TOPICS = List.of(ORDERS, PRODUCTS, CATEGORIES, STOCK);
  static final List<String> GROUPS =
      List.of(
          "recommendations.orders",
          "recommendations.products",
          "recommendations.categories",
          "recommendations.stock");

  private static final JsonMapper JSON = new JsonMapper();
  private static final JsonSchemaFactory SCHEMAS =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

  private Events() {}

  /** A new Order of the given Variants, placed now and not yet paid. */
  static OrderEvent order(String... variantIds) {
    return new OrderEvent(UUID.randomUUID().toString(), List.of(variantIds));
  }

  /**
   * A Product with the given Variants, in a Category of its own no event names, so it has no
   * Category's best sellers until {@link ProductEvent#in} says otherwise.
   */
  static ProductEvent product(String sku, String... variantIds) {
    return new ProductEvent(sku, List.of(variantIds));
  }

  /** A Category, with its name. */
  static CategoryEvent category(String slug, String name) {
    return new CategoryEvent(slug, name);
  }

  /** Puts each Variant in Stock, with 5 to sell. */
  static void inStock(String... variantIds) {
    for (var variantId : variantIds) {
      stock(variantId, 5).publish();
    }
  }

  /** A Variant's Stock: stocked, with {@code available} to sell. */
  static StockEvent stock(String variantId, int available) {
    return new StockEvent(variantId, available);
  }

  static final class OrderEvent {
    private final String orderId;
    private final List<String> variantIds;
    private long version = 1;
    private String status = "PLACED";
    private Instant placedAt = Instant.now();

    private OrderEvent(String orderId, List<String> variantIds) {
      this.orderId = orderId;
      this.variantIds = new ArrayList<>(variantIds);
    }

    /** The same Order, in {@code status} at {@code version}. */
    OrderEvent at(long version, String status) {
      this.version = version;
      this.status = status;
      return this;
    }

    OrderEvent placedAt(Instant placedAt) {
      this.placedAt = placedAt;
      return this;
    }

    /** Publishes it, and returns it so a test can publish a later version. */
    OrderEvent publish() {
      var money = Map.of("amountMinor", 1000, "currency", "EUR");
      var order = new LinkedHashMap<String, Object>();
      order.put("customerId", "customer-" + orderId);
      order.put("status", status);
      order.put("placedAt", placedAt.toString());
      order.put(
          "lines",
          variantIds.stream()
              .map(v -> Map.of("variantId", v, "quantity", 1, "unitPrice", money))
              .toList());
      order.put("discounts", List.of());
      order.put("tax", Map.of("amountMinor", 0, "currency", "EUR"));
      order.put("subtotal", money);
      order.put("total", money);
      order.put("statusHistory", List.of(Map.of("status", status, "at", placedAt.toString())));
      var change = version == 1 ? "PLACED" : "STATUS_CHANGED";
      send(ORDERS, orderId, envelope("orderId", orderId, version, change, "order", order));
      return this;
    }
  }

  static final class ProductEvent {
    private final String sku;
    private final List<String> variantIds;
    private long version = 1;
    private boolean removed;
    private String category;

    private ProductEvent(String sku, List<String> variantIds) {
      this.sku = sku;
      this.variantIds = variantIds;
      this.category = "own-" + sku.toLowerCase();
    }

    ProductEvent in(String category) {
      this.category = category;
      return this;
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
      product.put("category", category);
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

  static final class CategoryEvent {
    private final String slug;
    private final String name;
    private long version = 1;
    private boolean removed;

    private CategoryEvent(String slug, String name) {
      this.slug = slug;
      this.name = name;
    }

    CategoryEvent version(long version) {
      this.version = version;
      return this;
    }

    CategoryEvent removed() {
      this.removed = true;
      return this;
    }

    void publish() {
      var category = new LinkedHashMap<String, Object>();
      category.put("name", name);
      category.put("attributes", List.of());
      category.put("removed", removed);
      var change = removed ? "REMOVED" : version == 1 ? "CREATED" : "UPDATED";
      send(CATEGORIES, slug, envelope("slug", slug, version, change, "category", category));
    }
  }

  static final class StockEvent {
    private final String variantId;
    private final int available;
    private long version = 1;
    private boolean stocked = true;

    private StockEvent(String variantId, int available) {
      this.variantId = variantId;
      this.available = available;
    }

    StockEvent version(long version) {
      this.version = version;
      return this;
    }

    /** No longer stocked at all, as once Inventory removes the Variant. */
    StockEvent unstocked() {
      this.stocked = false;
      return this;
    }

    void publish() {
      var stock =
          Map.<String, Object>of("onHand", available, "available", available, "stocked", stocked);
      var change = stocked ? "ADJUSTED" : "REMOVED";
      send(STOCK, variantId, envelope("variantId", variantId, version, change, "stock", stock));
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
