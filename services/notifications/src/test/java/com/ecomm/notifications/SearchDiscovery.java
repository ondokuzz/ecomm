package com.ecomm.notifications;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only: Search & Discovery's read of one Product ({@code GET /products/{sku}}) in WireMock, as
 * the {@code ListedVariants} adapter calls it. A SKU given no Product is a 404, as Search answers
 * for one it doesn't hold, or holds removed.
 */
final class SearchDiscovery {

  static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());
  private static final JsonMapper JSON = new JsonMapper();

  static {
    SERVER.start();
    reset();
  }

  private SearchDiscovery() {}

  static void registerWith(DynamicPropertyRegistry registry) {
    registry.add("ecomm.notifications.search-discovery-url", SERVER::baseUrl);
  }

  static void reset() {
    SERVER.resetAll();
    SERVER.stubFor(get(urlPathMatching("/products/.*")).atPriority(10).willReturn(notFound()));
  }

  /** A Product as Search lists it, given its Variants with {@link Product#variant}. */
  static Product product(String sku, String name) {
    return new Product(sku, name);
  }

  static final class Product {
    private final String sku;
    private final String name;
    private final List<Map<String, Object>> variants = new ArrayList<>();

    private Product(String sku, String name) {
      this.sku = sku;
      this.name = name;
    }

    /** A Variant at {@code priceMinor} euros, in Stock or not. */
    Product variant(String variantId, long priceMinor, boolean inStock) {
      variants.add(
          Map.of(
              "id",
              variantId,
              "axisValues",
              Map.of(),
              "price",
              Map.of("amountMinor", priceMinor, "currency", "EUR"),
              "inStock",
              inStock));
      return this;
    }

    void listed() {
      var body = new LinkedHashMap<String, Object>();
      body.put("sku", sku);
      body.put("name", name);
      body.put("category", Map.of("slug", "phones", "name", "Phones"));
      body.put("attributes", Map.of());
      body.put("warranty", null);
      body.put("variants", variants);
      SERVER.stubFor(
          get(urlEqualTo("/products/" + sku)).willReturn(okJson(JSON.writeValueAsString(body))));
    }
  }
}
