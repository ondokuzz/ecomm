package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

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
    var products = JsonMapper.shared().readTree(Files.readString(CATALOG_SEED));
    var variantIds = new ArrayList<String>();
    for (var product : products) {
      var variants = product.get("variants");
      // A seed Product's first Variant keeps its SKU as its ID, so Carts and Orders stay valid.
      assertThat(variants.get(0).get("id").asString()).isEqualTo(product.get("sku").asString());
      variants.forEach(variant -> variantIds.add(variant.get("id").asString()));
    }

    assertThat(products).hasSize(20);
    assertThat(variantIds).hasSizeGreaterThan(20).doesNotHaveDuplicates();
    assertThat(variantIds).allSatisfy(id -> assertThat(quantityOf(id)).isNotNegative());
    assertThat(variantIds).filteredOn(id -> quantityOf(id) > 0).hasSizeGreaterThan(20);
  }
}
