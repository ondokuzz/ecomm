package com.ecomm.searchdiscovery;

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
 * Test-only: the events Search consumes, as Catalog and Inventory publish them. Each is checked
 * against its topic's schema in {@code platform/event-schemas} before it is sent, so a test can't
 * rely on an event no producer could publish.
 */
final class Events {

  static final String PRODUCTS = "catalog.product";
  static final String CATEGORIES = "catalog.category";
  static final String STOCK = "inventory.stock";
  static final List<String> TOPICS = List.of(PRODUCTS, CATEGORIES, STOCK);

  private static final JsonMapper JSON = new JsonMapper();
  private static final JsonSchemaFactory SCHEMAS =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

  private Events() {}

  static ProductEvent product(String sku) {
    return new ProductEvent(sku);
  }

  static CategoryEvent category(String slug) {
    return new CategoryEvent(slug);
  }

  /** A Variant's Stock: {@code available} units to sell, while Inventory stocks it. */
  static void stock(String variantId, long version, int available) {
    stock(variantId, version, available, true);
  }

  static void stock(String variantId, long version, int available, boolean stocked) {
    send(
        STOCK,
        variantId,
        envelope(
            "variantId",
            variantId,
            version,
            stocked ? "ADJUSTED" : "REMOVED",
            "stock",
            Map.of("onHand", available, "available", available, "stocked", stocked)));
  }

  static Variant variant(String id, long priceMinor, String currency) {
    return new Variant(id, priceMinor, currency);
  }

  static final class ProductEvent {
    private final String sku;
    private long version = 1;
    private String name;
    private String description;
    private String category = "phones";
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<String> images = new ArrayList<>();
    private final List<Variant> variants = new ArrayList<>();
    private boolean removed;

    private ProductEvent(String sku) {
      this.sku = sku;
      this.name = sku;
    }

    ProductEvent version(long version) {
      this.version = version;
      return this;
    }

    ProductEvent name(String name) {
      this.name = name;
      return this;
    }

    ProductEvent description(String description) {
      this.description = description;
      return this;
    }

    ProductEvent category(String category) {
      this.category = category;
      return this;
    }

    ProductEvent attribute(String name, String value) {
      attributes.put(name, value);
      return this;
    }

    ProductEvent image(String image) {
      images.add(image);
      return this;
    }

    ProductEvent variant(Variant variant) {
      variants.add(variant);
      return this;
    }

    /** A single Variant, with the SKU as its ID. */
    ProductEvent price(long amountMinor, String currency) {
      return variant(new Variant(sku, amountMinor, currency));
    }

    ProductEvent removed() {
      this.removed = true;
      return this;
    }

    void publish() {
      var product = new LinkedHashMap<String, Object>();
      product.put("name", name);
      if (description != null) {
        product.put("description", description);
      }
      product.put("category", category);
      product.put("attributes", attributes);
      product.put("images", images);
      product.put("variants", variants.stream().map(Variant::json).toList());
      product.put("removed", removed);
      var change = removed ? "REMOVED" : version == 1 ? "CREATED" : "UPDATED";
      send(PRODUCTS, sku, envelope("sku", sku, version, change, "product", product));
    }
  }

  static final class Variant {
    private final String id;
    private final long priceMinor;
    private final String currency;
    private final Map<String, String> axisValues = new LinkedHashMap<>();
    private final List<String> images = new ArrayList<>();

    private Variant(String id, long priceMinor, String currency) {
      this.id = id;
      this.priceMinor = priceMinor;
      this.currency = currency;
    }

    Variant axis(String name, String value) {
      axisValues.put(name, value);
      return this;
    }

    Variant image(String image) {
      images.add(image);
      return this;
    }

    private Map<String, Object> json() {
      return Map.of(
          "variantId",
          id,
          "axisValues",
          axisValues,
          "price",
          Map.of("amountMinor", priceMinor, "currency", currency),
          "images",
          images);
    }
  }

  static final class CategoryEvent {
    private final String slug;
    private long version = 1;
    private String name;
    private final List<Map<String, Object>> attributes = new ArrayList<>();
    private boolean removed;

    private CategoryEvent(String slug) {
      this.slug = slug;
      this.name = slug;
    }

    CategoryEvent version(long version) {
      this.version = version;
      return this;
    }

    CategoryEvent name(String name) {
      this.name = name;
      return this;
    }

    /** An attribute definition; {@code values} are an ENUM's. */
    CategoryEvent attribute(String name, String type, boolean variantAxis, String... values) {
      attributes.add(
          Map.of(
              "name",
              name,
              "type",
              type,
              "values",
              List.of(values),
              "required",
              false,
              "variantAxis",
              variantAxis));
      return this;
    }

    CategoryEvent removed() {
      this.removed = true;
      return this;
    }

    void publish() {
      var category = Map.of("name", name, "attributes", attributes, "removed", removed);
      var change = removed ? "REMOVED" : version == 1 ? "CREATED" : "UPDATED";
      send(CATEGORIES, slug, envelope("slug", slug, version, change, "category", category));
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
