package com.ecomm.returnswarranty;

import com.networknt.schema.JsonSchema;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only: Catalog's {@code catalog.product} events, as Catalog publishes them, each checked
 * against the topic's schema before it is sent.
 */
final class Products {

  static final String TOPIC = "catalog.product";
  static final String GROUP = "returns-warranty.products";

  private static final JsonMapper JSON = new JsonMapper();
  private static final JsonSchema SCHEMA = Shipments.loadSchema(TOPIC);

  private Products() {}

  /** A Product with the given Variants, without Warranty terms or Serial tracking, at version 1. */
  static Product product(String name, String... variantIds) {
    return new Product(name, List.of(variantIds));
  }

  static final class Product {
    private final String sku = "SKU-" + UUID.randomUUID();
    private final String name;
    private final List<String> variantIds;
    private long version = 1;
    private String serialTracking;
    private Integer warrantyMonths;
    private String manufacturer;

    private Product(String name, List<String> variantIds) {
      this.name = name;
      this.variantIds = variantIds;
    }

    /** Its Serial tracking: {@code NONE}, {@code SERIAL} or {@code IMEI}. */
    Product tracked(String serialTracking) {
      this.serialTracking = serialTracking;
      return this;
    }

    /** Its Warranty terms. */
    Product warranty(int months, String manufacturer) {
      this.warrantyMonths = months;
      this.manufacturer = manufacturer;
      return this;
    }

    /** Without Warranty terms. */
    Product noWarranty() {
      this.warrantyMonths = null;
      this.manufacturer = null;
      return this;
    }

    Product version(long version) {
      this.version = version;
      return this;
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
      if (warrantyMonths != null) {
        product.put("warranty", Map.of("months", warrantyMonths, "manufacturer", manufacturer));
      }
      var event = new LinkedHashMap<String, Object>();
      event.put("eventId", UUID.randomUUID().toString());
      event.put("occurredAt", Instant.now().toString());
      event.put("sku", sku);
      event.put("version", version);
      event.put("change", version == 1 ? "CREATED" : "UPDATED");
      event.put("product", product);
      Shipments.send(TOPIC, SCHEMA, sku, JSON.writeValueAsString(event));
      return this;
    }
  }
}
