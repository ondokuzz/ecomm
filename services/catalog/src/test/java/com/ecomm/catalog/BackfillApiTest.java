package com.ecomm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.java.json.JsonObject;
import com.ecomm.catalog.application.port.in.PublishBackfillUseCase;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Categories and Products stored before Catalog events, as a Sprint 2 stack holds them, are each
 * published once with change {@code BACKFILLED} when the service starts; starting again publishes
 * nothing more. The service runs against a bucket of its own here, holding only that older data.
 */
@TestPropertySource(properties = "ecomm.catalog.bucket=" + BackfillApiTest.BUCKET)
class BackfillApiTest extends CatalogApiTest {

  static final String BUCKET = "catalog-sprint2";

  private static final String CATEGORY = "sprint2-lamps";
  private static final String DESK_LAMP = "SPRINT2-DESK-LAMP";
  private static final String FLOOR_LAMP = "SPRINT2-FLOOR-LAMP";
  private static final String READING_LAMP = "SPRINT2-READING-LAMP";

  static {
    storeSprint2Catalog();
  }

  @Autowired PublishBackfillUseCase backfill;

  @Test
  void everyExistingCategoryAndProductIsPublishedOnceAsBackfilled() {
    var category = CatalogEvents.keyed(CATEGORY_TOPIC, CATEGORY, 1);
    var desk = CatalogEvents.keyed(PRODUCT_TOPIC, DESK_LAMP, 1);
    var floor = CatalogEvents.keyed(PRODUCT_TOPIC, FLOOR_LAMP, 1);

    assertThat(category).hasSize(1);
    assertThat(desk).hasSize(1);
    assertThat(floor).hasSize(1);
    for (var record : List.of(category.getFirst(), desk.getFirst(), floor.getFirst())) {
      assertThat(CatalogEvents.schemaViolationsOf(record)).isEmpty();
      var event = CatalogEvents.valueOf(record);
      assertThat(event.get("change").asText()).isEqualTo("BACKFILLED");
      assertThat(event.get("version").asLong()).isPositive();
    }
    var categoryEvent = CatalogEvents.valueOf(category.getFirst());
    assertThat(categoryEvent.at("/category/name").asText()).isEqualTo("Lamps");
    assertThat(categoryEvent.at("/category/attributes/1/values/0").asText()).isEqualTo("Brass");
    var deskEvent = CatalogEvents.valueOf(desk.getFirst());
    assertThat(deskEvent.at("/product/name").asText()).isEqualTo("Desk lamp");
    assertThat(deskEvent.get("product").has("description")).isFalse();
    assertThat(deskEvent.at("/product/variants/1/variantId").asText())
        .isEqualTo("SPRINT2-DESK-LAMP-STEEL");
    assertThat(deskEvent.at("/product/variants/1/price/amountMinor").asLong()).isEqualTo(5900);
  }

  @Test
  void runningTheBackfillAgainPublishesNothingMore() {
    CatalogEvents.keyed(PRODUCT_TOPIC, DESK_LAMP, 1);

    assertThat(backfill.publishBackfill()).isZero();

    assertThat(CatalogEvents.keyed(PRODUCT_TOPIC, DESK_LAMP, 2, Duration.ofSeconds(4))).hasSize(1);
    assertThat(CatalogEvents.keyed(CATEGORY_TOPIC, CATEGORY, 2, Duration.ofSeconds(3))).hasSize(1);
  }

  @Test
  void aBackfilledProductMovesOnFromItsBackfilledVersion() {
    CatalogEvents.keyed(PRODUCT_TOPIC, READING_LAMP, 1);

    http.put()
        .uri("/products/{sku}", READING_LAMP)
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"name": "Reading lamp", "category": "sprint2-lamps", "attributes": {"bulb": "E14"},
             "variants": [{"id": "SPRINT2-READING-LAMP", "axisValues": {"finish": "Steel"},
                           "price": {"amountMinor": 3900, "currency": "EUR"}}]}
            """)
        .exchange()
        .expectStatus()
        .isOk();

    var events = CatalogEvents.valuesOf(CatalogEvents.keyed(PRODUCT_TOPIC, READING_LAMP, 2));
    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("BACKFILLED", "UPDATED");
    assertThat(events)
        .extracting(e -> e.get("version").asLong())
        .doesNotHaveDuplicates()
        .isSortedAccordingTo(Comparator.naturalOrder());
  }

  /** A Category and its Products as Sprint 2's Catalog stored them: no versions, no outbox. */
  private static void storeSprint2Catalog() {
    var cluster = connect();
    try {
      var bucket = cluster.bucket(BUCKET);
      bucket.waitUntilReady(Duration.ofSeconds(60));
      var documents = bucket.defaultCollection();
      documents.upsert(
          "category::" + CATEGORY,
          JsonObject.fromJson(
              """
              {"type": "category", "slug": "sprint2-lamps", "name": "Lamps", "attributes": [
                {"name": "bulb", "type": "TEXT", "values": [], "required": true,
                 "variantAxis": false},
                {"name": "finish", "type": "ENUM", "values": ["Brass", "Steel"],
                 "required": true, "variantAxis": true}]}
              """));
      documents.upsert(
          DESK_LAMP,
          JsonObject.fromJson(
              """
              {"type": "product", "sku": "SPRINT2-DESK-LAMP", "name": "Desk lamp",
               "category": "sprint2-lamps", "attributes": {"bulb": "E14"}, "images": [],
               "variants": [
                 {"id": "SPRINT2-DESK-LAMP", "axisValues": [{"name": "finish", "value": "Brass"}],
                  "price": {"amountMinor": 4900, "currency": "EUR"}, "images": []},
                 {"id": "SPRINT2-DESK-LAMP-STEEL",
                  "axisValues": [{"name": "finish", "value": "Steel"}],
                  "price": {"amountMinor": 5900, "currency": "EUR"}, "images": []}]}
              """));
      documents.upsert(
          FLOOR_LAMP,
          JsonObject.fromJson(
              """
              {"type": "product", "sku": "SPRINT2-FLOOR-LAMP", "name": "Floor lamp",
               "category": "sprint2-lamps", "attributes": {"bulb": "E27"}, "images": [],
               "variants": [
                 {"id": "SPRINT2-FLOOR-LAMP", "axisValues": [{"name": "finish", "value": "Brass"}],
                  "price": {"amountMinor": 11900, "currency": "EUR"}, "images": []}]}
              """));
      documents.upsert(
          READING_LAMP,
          JsonObject.fromJson(
              """
              {"type": "product", "sku": "SPRINT2-READING-LAMP", "name": "Reading lamp",
               "category": "sprint2-lamps", "attributes": {"bulb": "E14"}, "images": [],
               "variants": [
                 {"id": "SPRINT2-READING-LAMP",
                  "axisValues": [{"name": "finish", "value": "Steel"}],
                  "price": {"amountMinor": 3500, "currency": "EUR"}, "images": []}]}
              """));
    } finally {
      cluster.disconnect();
    }
  }
}
