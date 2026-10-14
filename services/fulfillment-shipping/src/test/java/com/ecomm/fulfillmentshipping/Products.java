package com.ecomm.fulfillmentshipping;

import com.ecomm.commons.events.EventBackbone;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 * Test-only: Catalog's {@code catalog.product} events, as Catalog publishes them, each checked
 * against the topic's schema before it is sent.
 */
final class Products {

  static final String TOPIC = "catalog.product";
  static final String GROUP = "fulfillment-shipping.products";

  private static final JsonMapper JSON = new JsonMapper();
  private static final JsonSchema SCHEMA = loadSchema();

  private Products() {}

  /** A Product with the given Variants, not serial-tracked, at version 1. */
  static Product product(String name, String... variantIds) {
    return new Product(name, List.of(variantIds));
  }

  static final class Product {
    private final String sku = "SKU-" + UUID.randomUUID();
    private String name;
    private final List<String> variantIds;
    private long version = 1;
    private String serialTracking;

    private Product(String name, List<String> variantIds) {
      this.name = name;
      this.variantIds = variantIds;
    }

    /** Its Serial tracking: {@code NONE}, {@code SERIAL} or {@code IMEI}. */
    Product tracked(String serialTracking) {
      this.serialTracking = serialTracking;
      return this;
    }

    Product version(long version) {
      this.version = version;
      return this;
    }

    /** Renames it, for a later version to publish. */
    Product named(String name) {
      this.name = name;
      return this;
    }

    String sku() {
      return sku;
    }

    Product publish() {
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
                          Map.of(),
                          "price",
                          Map.of("amountMinor", 79900, "currency", "EUR"),
                          "images",
                          List.of()))
              .toList());
      product.put("removed", false);
      if (serialTracking != null) {
        product.put("serialTracking", serialTracking);
      }
      var event = new LinkedHashMap<String, Object>();
      event.put("eventId", UUID.randomUUID().toString());
      event.put("occurredAt", Instant.now().toString());
      event.put("sku", sku());
      event.put("version", version);
      event.put("change", version == 1 ? "CREATED" : "UPDATED");
      event.put("product", product);
      send(sku(), JSON.writeValueAsString(event));
      return this;
    }
  }

  private static void send(String key, String json) {
    var errors = SCHEMA.validate(json, InputFormat.JSON);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(TOPIC + " event breaks its schema: " + errors);
    }
    try (var producer =
        new KafkaProducer<String, String>(
            Map.of("bootstrap.servers", EventBackbone.bootstrapServers()),
            new StringSerializer(),
            new StringSerializer())) {
      producer.send(new ProducerRecord<>(TOPIC, key, json)).get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static JsonSchema loadSchema() {
    try (var in =
        Products.class.getClassLoader().getResourceAsStream("event-schemas/" + TOPIC + ".json")) {
      return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
