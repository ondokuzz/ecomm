package com.ecomm.searchdiscovery;

import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Test-only: {@code GET /search} as a client sees it, independent of the service's own classes. */
final class SearchClient {

  record Money(long amountMinor, String currency) {}

  record Summary(
      String sku,
      String name,
      String image,
      Money priceFrom,
      boolean priceVaries,
      boolean inStock,
      Map<String, String> attributes) {}

  record CategoryCount(String slug, String name, long count) {}

  record ValueCount(String value, long count) {}

  record AttributeFacet(
      String name, String type, List<ValueCount> values, BigDecimal min, BigDecimal max) {}

  record PriceRange(String currency, long min, long max) {}

  record Facets(
      List<CategoryCount> categories,
      List<AttributeFacet> attributes,
      List<PriceRange> prices,
      long inStock) {}

  record Results(List<Summary> items, int page, int size, long total, Facets facets) {

    List<String> skus() {
      return items.stream().map(Summary::sku).toList();
    }

    Summary item(String sku) {
      return items.stream().filter(i -> i.sku().equals(sku)).findFirst().orElseThrow();
    }

    long categoryCount(String slug) {
      return facets.categories().stream()
          .filter(c -> c.slug().equals(slug))
          .mapToLong(CategoryCount::count)
          .findFirst()
          .orElse(0);
    }

    AttributeFacet attribute(String name) {
      return facets.attributes().stream()
          .filter(a -> a.name().equals(name))
          .findFirst()
          .orElseThrow();
    }

    /** Each of the attribute's values with its count, in the facet's order. */
    Map<String, Long> valueCounts(String attribute) {
      var counts = new LinkedHashMap<String, Long>();
      attribute(attribute).values().forEach(v -> counts.put(v.value(), v.count()));
      return counts;
    }
  }

  private final RestTestClient http;

  SearchClient(RestTestClient http) {
    this.http = http;
  }

  /** A category slug no other test uses, so a search within it sees only this test's Products. */
  static String newCategory() {
    return "cat-" + UUID.randomUUID().toString().substring(0, 8);
  }

  /** A word no other test's Products contain. */
  static String newWord() {
    return "w" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  Results search(String query) {
    return http.get()
        .uri("/search?" + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(Results.class)
        .returnResult()
        .getResponseBody();
  }

  /** The search's results once they satisfy {@code until}, as events take a moment to apply. */
  Results awaitSearch(String query, Predicate<Results> until) {
    return await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> search(query), until);
  }
}
