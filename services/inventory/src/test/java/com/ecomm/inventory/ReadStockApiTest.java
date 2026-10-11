package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/** Anyone can read a Variant's stock, or several Variants' at once, without logging in. */
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
        .isNumber()
        .jsonPath("$.onHand")
        .isNumber()
        .jsonPath("$.reserved")
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
  void anyoneCanReadSeveralVariantsStockAtOnceInTheOrderAskedLeavingOutUnknownOnes() {
    var first = newVariant(3);
    var second = newVariant(0);

    var stocks = stocksOf(second + ",NO-SUCH-VARIANT," + first + "," + second);

    assertThat(stocks)
        .containsExactly(new StockView(second, 0, 0, 0), new StockView(first, 3, 3, 0));
  }

  @Test
  void variantIdsMayBeRepeatedInsteadOfCommaSeparated() {
    var first = newVariant(3);
    var second = newVariant(4);

    var stocks =
        http.get()
            .uri("/stock?variantIds={a}&variantIds={b}", first, second)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(StockView[].class)
            .returnResult()
            .getResponseBody();

    assertThat(stocks)
        .containsExactly(new StockView(first, 3, 3, 0), new StockView(second, 4, 4, 0));
  }

  @Test
  void aBatchOfOnlyUnknownVariantsIsEmpty() {
    assertThat(stocksOf("NO-SUCH-VARIANT")).isEmpty();
  }

  @Test
  void aBatchOfFiftyVariantsCanBeRead() {
    var ids = IntStream.range(0, 50).mapToObj(i -> "NO-SUCH-VARIANT-" + i).toList();

    assertThat(stocksOf(String.join(",", ids))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/stock", "/stock?variantIds=", "/stock?variantIds=,"})
  void aBatchNamingNoVariantIsABadRequest(String uri) {
    expectBadRequest(uri);
  }

  @Test
  void aBatchOfMoreThanFiftyVariantsIsABadRequest() {
    var ids = IntStream.range(0, 51).mapToObj(i -> "V-" + i).toList();

    expectBadRequest("/stock?variantIds=" + String.join(",", ids));
  }

  private StockView[] stocksOf(String variantIds) {
    return http.get()
        .uri("/stock?variantIds={ids}", variantIds)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(StockView[].class)
        .returnResult()
        .getResponseBody();
  }

  private void expectBadRequest(String uri) {
    http.get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
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
