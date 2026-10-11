package com.ecomm.recommendations;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Test-only: reads Recommendations over HTTP, without a token, as anyone may. */
final class RecommendationsClient {

  record AlsoBought(String sku, List<Item> items) {
    List<String> skus() {
      return items.stream().map(Item::sku).toList();
    }
  }

  record Item(String sku, long orders) {}

  private final RestTestClient http;

  RecommendationsClient(RestTestClient http) {
    this.http = http;
  }

  /** A SKU no other test uses. */
  static String newSku() {
    return "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  AlsoBought alsoBought(String sku) {
    return http.get()
        .uri("/products/{sku}/also-bought", sku)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(AlsoBought.class)
        .returnResult()
        .getResponseBody();
  }

  AlsoBought alsoBought(String sku, int limit) {
    return http.get()
        .uri("/products/{sku}/also-bought?limit={limit}", sku, limit)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(AlsoBought.class)
        .returnResult()
        .getResponseBody();
  }

  /** Polls until what was bought with {@code sku} satisfies {@code until}, as events land. */
  AlsoBought awaitAlsoBought(String sku, Predicate<AlsoBought> until) {
    return await().atMost(Duration.ofSeconds(30)).until(() -> alsoBought(sku), until);
  }
}
