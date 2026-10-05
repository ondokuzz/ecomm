package com.ecomm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Creating, changing or removing a Product publishes one {@code catalog.product} event: keyed by
 * its SKU, valid against its schema, carrying the request's Correlation ID, the whole Product, and
 * a higher version than the last. A write that is refused publishes nothing.
 */
class ProductEventsApiTest extends CatalogApiTest {

  /** The Category these tests' Products belong to; a 409 means an earlier test created it. */
  @BeforeEach
  void gadgetsCategory() {
    http.post()
        .uri("/categories")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"slug": "event-gadgets", "name": "Gadgets", "attributes": [
              {"name": "brand", "type": "TEXT", "required": true, "variantAxis": false},
              {"name": "colour", "type": "TEXT", "required": true, "variantAxis": true}]}
            """)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isIn(201, 409));
  }

  @Test
  void creatingAProductPublishesTheWholeProduct() {
    var sku = newSku();

    create("event-create-1", gadget(sku, "Gadget", 1999)).expectStatus().isCreated();

    var records = CatalogEvents.keyed(PRODUCT_TOPIC, sku, 1);
    assertThat(records).hasSize(1);
    var record = records.getFirst();
    assertThat(CatalogEvents.schemaViolationsOf(record)).isEmpty();
    assertThat(CatalogEvents.correlationIdOf(record)).isEqualTo("event-create-1");
    var event = CatalogEvents.valueOf(record);
    assertThat(event.get("sku").asText()).isEqualTo(sku);
    assertThat(event.get("change").asText()).isEqualTo("CREATED");
    assertThat(event.get("version").asLong()).isPositive();
    var product = event.get("product");
    assertThat(product.get("name").asText()).isEqualTo("Gadget");
    assertThat(product.get("description").asText()).isEqualTo("A gadget for testing events.");
    assertThat(product.get("category").asText()).isEqualTo("event-gadgets");
    assertThat(product.at("/attributes/brand").asText()).isEqualTo("Acme");
    assertThat(product.at("/images/0").asText()).isEqualTo("/images/gadget.svg");
    assertThat(product.at("/variants/0/variantId").asText()).isEqualTo(sku + "-RED");
    assertThat(product.at("/variants/0/axisValues/colour").asText()).isEqualTo("Red");
    assertThat(product.at("/variants/0/price/amountMinor").asLong()).isEqualTo(1999);
    assertThat(product.at("/variants/0/price/currency").asText()).isEqualTo("EUR");
    assertThat(product.get("removed").asBoolean()).isFalse();
  }

  @Test
  void eachChangePublishesTheProductAsItNowIsWithAHigherVersion() {
    var sku = newSku();
    create("event-change-1", gadget(sku, "Gadget", 1999)).expectStatus().isCreated();
    replace(sku, "event-change-2", gadget(sku, "Gadget Pro", 2499)).expectStatus().isOk();
    delete(sku, "event-change-3").expectStatus().isNoContent();

    var records = CatalogEvents.keyed(PRODUCT_TOPIC, sku, 3);

    assertThat(records).allSatisfy(r -> assertThat(CatalogEvents.schemaViolationsOf(r)).isEmpty());
    assertThat(records)
        .extracting(CatalogEvents::correlationIdOf)
        .containsExactly("event-change-1", "event-change-2", "event-change-3");
    var events = CatalogEvents.valuesOf(records);
    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("CREATED", "UPDATED", "REMOVED");
    assertThat(events)
        .extracting(e -> e.get("version").asLong())
        .doesNotHaveDuplicates()
        .isSortedAccordingTo(Comparator.naturalOrder());
    assertThat(events)
        .extracting(e -> e.at("/product/name").asText())
        .containsExactly("Gadget", "Gadget Pro", "Gadget Pro");
    assertThat(events)
        .extracting(e -> e.at("/product/variants/0/price/amountMinor").asLong())
        .containsExactly(1999L, 2499L, 2499L);
    assertThat(events)
        .extracting(e -> e.at("/product/removed").asBoolean())
        .containsExactly(false, false, true);
  }

  @Test
  void aProductCreatedAgainAfterItsRemovalCarriesOnFromItsLastVersion() {
    var sku = newSku();
    create("event-again-1", gadget(sku, "Gadget", 1999)).expectStatus().isCreated();
    delete(sku, "event-again-2").expectStatus().isNoContent();

    create("event-again-3", gadget(sku, "Gadget Reborn", 999)).expectStatus().isCreated();

    var events = CatalogEvents.valuesOf(CatalogEvents.keyed(PRODUCT_TOPIC, sku, 3));
    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("CREATED", "REMOVED", "CREATED");
    assertThat(events)
        .extracting(e -> e.get("version").asLong())
        .doesNotHaveDuplicates()
        .isSortedAccordingTo(Comparator.naturalOrder());
    assertThat(events.getLast().at("/product/removed").asBoolean()).isFalse();
  }

  @Test
  void aProductWithoutADescriptionLeavesItOut() {
    var sku = newSku();

    create(
            "event-plain-1",
            gadget(sku, "Gadget", 1999)
                .replace("\"description\": \"A gadget for testing events.\",", ""))
        .expectStatus()
        .isCreated();

    var record = CatalogEvents.keyed(PRODUCT_TOPIC, sku, 1).getFirst();
    assertThat(CatalogEvents.schemaViolationsOf(record)).isEmpty();
    assertThat(CatalogEvents.valueOf(record).get("product").has("description")).isFalse();
  }

  @Test
  void aRefusedWritePublishesNothing() {
    var sku = newSku();
    create("event-refused-1", gadget(sku, "Gadget", 1999)).expectStatus().isCreated();

    // Taken SKU, invalid attributes, a missing Product, and a dropped Variant: all refused.
    create("event-refused-2", gadget(sku, "Gadget", 1999)).expectStatus().isEqualTo(409);
    replace(sku, "event-refused-3", gadget(sku, "Gadget", 1999).replace("brand", "make"))
        .expectStatus()
        .isBadRequest();
    replace(sku, "event-refused-4", gadget(sku, "Gadget", 1999).replace(sku + "-RED", sku + "-X"))
        .expectStatus()
        .isEqualTo(409);
    var missing = newSku();
    replace(missing, "event-refused-5", gadget(missing, "Gadget", 1)).expectStatus().isNotFound();
    delete(missing, "event-refused-6").expectStatus().isNotFound();

    assertThat(CatalogEvents.keyed(PRODUCT_TOPIC, sku, 2, Duration.ofSeconds(5))).hasSize(1);
    assertThat(CatalogEvents.keyed(PRODUCT_TOPIC, missing, 1, Duration.ofSeconds(3))).isEmpty();
  }

  static String newSku() {
    return "EVT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  static String gadget(String sku, String name, long amountMinor) {
    return """
        {"sku": "%s", "name": "%s", "description": "A gadget for testing events.",
         "category": "event-gadgets", "attributes": {"brand": "Acme"},
         "images": ["/images/gadget.svg"],
         "variants": [{"id": "%s-RED", "axisValues": {"colour": "Red"},
                       "price": {"amountMinor": %d, "currency": "EUR"}}]}
        """
        .formatted(sku, name, sku, amountMinor);
  }

  private RestTestClient.ResponseSpec replace(String sku, String correlationId, String body) {
    return http.put()
        .uri("/products/{sku}", sku)
        .headers(h -> h.setBearerAuth(staffToken()))
        .header("X-Correlation-Id", correlationId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  private RestTestClient.ResponseSpec delete(String sku, String correlationId) {
    return http.delete()
        .uri("/products/{sku}", sku)
        .headers(h -> h.setBearerAuth(staffToken()))
        .header("X-Correlation-Id", correlationId)
        .exchange();
  }

  private RestTestClient.ResponseSpec create(String correlationId, String body) {
    return http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .header("X-Correlation-Id", correlationId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }
}
