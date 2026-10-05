package com.ecomm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Creating, changing or removing a Category publishes one {@code catalog.category} event: keyed by
 * its slug, valid against its schema, carrying the request's Correlation ID, its attribute
 * definitions, and a higher version than the last. A write that is refused publishes nothing.
 */
class CategoryEventsApiTest extends CatalogApiTest {

  @Test
  void eachChangePublishesTheCategoryAsItNowIsWithAHigherVersion() {
    var slug = newSlug();
    create("category-change-1", category(slug, "Kites")).expectStatus().isCreated();
    replace(slug, "category-change-2", category(slug, "Stunt kites")).expectStatus().isOk();
    delete(slug, "category-change-3").expectStatus().isNoContent();

    var records = CatalogEvents.keyed(CATEGORY_TOPIC, slug, 3);

    assertThat(records).allSatisfy(r -> assertThat(CatalogEvents.schemaViolationsOf(r)).isEmpty());
    assertThat(records)
        .extracting(CatalogEvents::correlationIdOf)
        .containsExactly("category-change-1", "category-change-2", "category-change-3");
    var events = CatalogEvents.valuesOf(records);
    assertThat(events).extracting(e -> e.get("slug").asText()).containsOnly(slug);
    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("CREATED", "UPDATED", "REMOVED");
    assertThat(events)
        .extracting(e -> e.get("version").asLong())
        .doesNotHaveDuplicates()
        .isSortedAccordingTo(Comparator.naturalOrder());
    assertThat(events)
        .extracting(e -> e.at("/category/name").asText())
        .containsExactly("Kites", "Stunt kites", "Stunt kites");
    assertThat(events)
        .extracting(e -> e.at("/category/removed").asBoolean())
        .containsExactly(false, false, true);
    var first = events.getFirst();
    assertThat(first.at("/category/attributes/0/name").asText()).isEqualTo("brand");
    assertThat(first.at("/category/attributes/0/type").asText()).isEqualTo("TEXT");
    assertThat(first.at("/category/attributes/0/required").asBoolean()).isTrue();
    assertThat(first.at("/category/attributes/1/name").asText()).isEqualTo("size");
    assertThat(first.at("/category/attributes/1/type").asText()).isEqualTo("ENUM");
    assertThat(first.at("/category/attributes/1/values/1").asText()).isEqualTo("L");
    assertThat(first.at("/category/attributes/1/variantAxis").asBoolean()).isTrue();
  }

  @Test
  void aRefusedWritePublishesNothing() {
    var slug = newSlug();
    create("category-refused-1", category(slug, "Kites")).expectStatus().isCreated();
    http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"sku": "%s-KITE", "name": "Kite", "category": "%s", "attributes": {"brand": "Acme"},
             "variants": [{"id": "%s-KITE-M", "axisValues": {"size": "M"},
                           "price": {"amountMinor": 100, "currency": "EUR"}}]}
            """
                .formatted(slug, slug, slug))
        .exchange()
        .expectStatus()
        .isCreated();

    // A taken slug, a malformed definition, and a delete while it still has a Product: all refused.
    create("category-refused-2", category(slug, "Kites")).expectStatus().isEqualTo(409);
    replace(slug, "category-refused-3", category(slug, "Kites").replace("TEXT", "COLOUR"))
        .expectStatus()
        .isBadRequest();
    delete(slug, "category-refused-4").expectStatus().isEqualTo(409);
    var missing = newSlug();
    replace(missing, "category-refused-5", category(missing, "Nothing"))
        .expectStatus()
        .isNotFound();
    delete(missing, "category-refused-6").expectStatus().isNotFound();

    assertThat(CatalogEvents.keyed(CATEGORY_TOPIC, slug, 2, Duration.ofSeconds(5))).hasSize(1);
    assertThat(CatalogEvents.keyed(CATEGORY_TOPIC, missing, 1, Duration.ofSeconds(3))).isEmpty();
  }

  private static String newSlug() {
    return "events-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static String category(String slug, String name) {
    return """
        {"slug": "%s", "name": "%s", "attributes": [
          {"name": "brand", "type": "TEXT", "required": true, "variantAxis": false},
          {"name": "size", "type": "ENUM", "values": ["M", "L"], "required": true,
           "variantAxis": true}]}
        """
        .formatted(slug, name);
  }

  private RestTestClient.ResponseSpec create(String correlationId, String body) {
    return http.post()
        .uri("/categories")
        .headers(h -> h.setBearerAuth(staffToken()))
        .header("X-Correlation-Id", correlationId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  private RestTestClient.ResponseSpec replace(String slug, String correlationId, String body) {
    return http.put()
        .uri("/categories/{slug}", slug)
        .headers(h -> h.setBearerAuth(staffToken()))
        .header("X-Correlation-Id", correlationId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  private RestTestClient.ResponseSpec delete(String slug, String correlationId) {
    return http.delete()
        .uri("/categories/{slug}", slug)
        .headers(h -> h.setBearerAuth(staffToken()))
        .header("X-Correlation-Id", correlationId)
        .exchange();
  }
}
