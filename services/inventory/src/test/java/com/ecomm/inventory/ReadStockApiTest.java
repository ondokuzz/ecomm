package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Anyone can read a Variant's stock without logging in. */
class ReadStockApiTest extends InventoryApiTest {

  /** Catalog's seed data; the test runs from this module's directory. */
  private static final Path CATALOG_SEED =
      Path.of("../catalog/src/main/resources/seed/products.json");

  @Test
  void anyoneCanReadAVariantsStock() {
    http.get()
        .uri("/stock/{variantId}", "PHN-PIXEL-9")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.variantId")
        .isEqualTo("PHN-PIXEL-9")
        .jsonPath("$.quantity")
        .isNumber();
  }

  @Test
  void anUnknownVariantIsNotFound() {
    http.get()
        .uri("/stock/{variantId}", "NO-SUCH-VARIANT")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(404);
  }

  @Test
  void everyCatalogSeedVariantHasSeedStock() throws IOException {
    var skus =
        Pattern.compile("\"sku\"\\s*:\\s*\"([^\"]+)\"")
            .matcher(Files.readString(CATALOG_SEED))
            .results()
            .map(m -> m.group(1))
            .toList();

    assertThat(skus).hasSize(20);
    // Until multi-Variant Products arrive, a seed Product's only Variant ID is its SKU.
    assertThat(skus).allSatisfy(sku -> assertThat(quantityOf(sku)).isPositive());
  }
}
