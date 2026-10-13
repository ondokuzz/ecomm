package com.ecomm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;

/**
 * Seed data goes only into an empty bucket, so a restart never undoes Staff changes. The one
 * exception is Warranty terms and Serial tracking: a seeded Product stored before they existed is
 * given the seed's, and nothing else of it changes.
 */
class SeedDataApiTest extends CatalogApiTest {

  @Autowired ApplicationContext context;

  @ParameterizedTest
  @CsvSource({
    "PHN-PIXEL-9,       IMEI,   24, Google",
    "LPT-XPS-13,        SERIAL, 24, Dell",
    "AUD-BOSE-QC-ULTRA, SERIAL, 12, Bose",
  })
  void seededPhonesLaptopsAndAudioHaveWarrantyTermsAndSerialTracking(
      String sku, String serialTracking, int months, String manufacturer) {
    http.get()
        .uri("/products/{sku}", sku)
        .exchange()
        .expectBody()
        .jsonPath("$.serialTracking")
        .isEqualTo(serialTracking)
        .jsonPath("$.warranty.months")
        .isEqualTo(months)
        .jsonPath("$.warranty.manufacturer")
        .isEqualTo(manufacturer);
  }

  @Test
  void aSeededProductStoredBeforeWarrantyTermsGainsTheSeedsOnceAndIsPublished() throws Exception {
    storeAsBeforeWarrantyTerms("LPT-SURFACE-7", "Surface Laptop 7, renamed by Staff");
    var before = CatalogEvents.keyed(PRODUCT_TOPIC, "LPT-SURFACE-7", 1).size();

    runRunners();

    http.get()
        .uri("/products/LPT-SURFACE-7")
        .exchange()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Surface Laptop 7, renamed by Staff")
        .jsonPath("$.serialTracking")
        .isEqualTo("SERIAL")
        .jsonPath("$.warranty.months")
        .isEqualTo(24)
        .jsonPath("$.warranty.manufacturer")
        .isEqualTo("Microsoft");
    var events = CatalogEvents.keyed(PRODUCT_TOPIC, "LPT-SURFACE-7", before + 1);
    assertThat(events).hasSize(before + 1);
    assertThat(CatalogEvents.schemaViolationsOf(events.getLast())).isEmpty();
    var event = CatalogEvents.valueOf(events.getLast());
    assertThat(event.get("change").asText()).isEqualTo("UPDATED");
    assertThat(event.at("/product/name").asText()).isEqualTo("Surface Laptop 7, renamed by Staff");
    assertThat(event.at("/product/serialTracking").asText()).isEqualTo("SERIAL");
    assertThat(event.at("/product/warranty/manufacturer").asText()).isEqualTo("Microsoft");

    runRunners();

    assertThat(
            CatalogEvents.keyed(PRODUCT_TOPIC, "LPT-SURFACE-7", before + 2, Duration.ofSeconds(4)))
        .hasSize(before + 1);
  }

  @Test
  void aSeededProductStaffSavedWithoutWarrantyTermsKeepsNone() throws Exception {
    storeAsBeforeWarrantyTerms("PHN-NOTHING-2A", "Nothing Phone (2a)");
    http.put()
        .uri("/products/PHN-NOTHING-2A")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"name": "Nothing Phone (2a)", "category": "phones",
             "attributes": {"brand": "Nothing", "screen": "6.7 in"},
             "variants": [{"id": "PHN-NOTHING-2A",
                           "axisValues": {"color": "White", "storage": "128 GB"},
                           "price": {"amountMinor": 32900, "currency": "EUR"}}]}
            """)
        .exchange()
        .expectStatus()
        .isOk();

    runRunners();

    http.get()
        .uri("/products/PHN-NOTHING-2A")
        .exchange()
        .expectBody()
        .jsonPath("$.warranty")
        .isEmpty()
        .jsonPath("$.serialTracking")
        .isEqualTo("NONE");
  }

  /** Rewrites a seeded Product's document as a Sprint 4 Catalog stored it, under {@code name}. */
  private static void storeAsBeforeWarrantyTerms(String sku, String name) {
    var cluster = connect();
    try {
      var documents = cluster.bucket("catalog").defaultCollection();
      var document = documents.get(sku).contentAsObject();
      document.removeKey("warranty");
      document.removeKey("serialTracking");
      document.put("name", name);
      documents.replace(sku, document);
    } finally {
      cluster.disconnect();
    }
  }

  private void runRunners() throws Exception {
    for (var runner : context.getBeansOfType(ApplicationRunner.class).values()) {
      runner.run(new DefaultApplicationArguments());
    }
  }

  @Test
  void restartingDoesNotBringBackASeededProductStaffDeleted() throws Exception {
    http.delete()
        .uri("/products/AUD-SONOS-ERA-100")
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isNoContent();

    runRunners();

    http.get().uri("/products/AUD-SONOS-ERA-100").exchange().expectStatus().isNotFound();
  }
}
