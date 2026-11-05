package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Asked about the Catalog's Products, the mock provider answers from Search & Discovery: whether a
 * Variant is in Stock and its Price, a Product's Warranty terms, and a few Products for a query,
 * each named with its Price and linked to its page.
 */
class ProductsApiTest extends SupportAssistantApiTest {

  private static void stubPixel9(String q) {
    stubSearch(
        q,
        productSummary("PHN-PIXEL-9-PRO", "Google Pixel 9 Pro", 109900, false, true),
        productSummary("PHN-PIXEL-9", "Google Pixel 9", 79900, true, true));
    stubProduct("PHN-PIXEL-9", okJson(PIXEL_9));
  }

  @Test
  void aVariantsStockIsAnsweredWithItsPrice() {
    stubPixel9("Pixel 9 256 GB black");
    var id = started();

    send(id, "Is the Pixel 9 in 256 GB, black, in stock?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("The Google Pixel 9 is out of Stock in Obsidian, 256 GB at 899.00 EUR.")
        .jsonPath("$.references[*].id")
        .isEqualTo(List.of("PHN-PIXEL-9"))
        .jsonPath("$.references[0].kind")
        .isEqualTo("PRODUCT")
        .jsonPath("$.references[0].label")
        .isEqualTo("Google Pixel 9");

    SEARCH_DISCOVERY.verify(
        getRequestedFor(urlPathEqualTo("/products/PHN-PIXEL-9"))
            .withHeader("Authorization", equalTo("Bearer " + customerToken()))
            .withHeader("X-Correlation-Id", equalTo("test-correlation-1")));
  }

  @Test
  void aProductAskedAboutWithoutAVariantHasEachOnesStockAndPrice() {
    stubPixel9("Pixel 9");
    var id = started();

    send(id, "Is the Pixel 9 available?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "The Google Pixel 9 is in Stock in Obsidian, 128 GB at 799.00 EUR and Porcelain, 128"
                + " GB at 799.00 EUR, and out of Stock in Obsidian, 256 GB at 899.00 EUR.");
  }

  @Test
  void aProductsWarrantyTermsAreAnswered() {
    stubPixel9("Pixel 9");
    var id = started();

    send(id, "Does the Pixel 9 come with a warranty?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "The Google Pixel 9 comes with Google's 24-month warranty, counted from the day it is"
                + " delivered.")
        .jsonPath("$.references[0].id")
        .isEqualTo("PHN-PIXEL-9");
  }

  @Test
  void aProductWithoutWarrantyTermsHasNone() {
    stubSearch("JBL Flip 6", productSummary("AUD-JBL-FLIP-6", "JBL Flip 6", 12900, false, true));
    stubProduct(
        "AUD-JBL-FLIP-6",
        okJson(
            """
            {"sku": "AUD-JBL-FLIP-6", "name": "JBL Flip 6",
             "category": {"slug": "audio", "name": "Audio"}, "attributes": {}, "warranty": null,
             "variants": [{"id": "AUD-JBL-FLIP-6", "axisValues": {},
                           "price": {"amountMinor": 12900, "currency": "EUR"}, "inStock": true}]}
            """));
    var id = started();

    send(id, "What warranty does the JBL Flip 6 have?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("The JBL Flip 6 has no warranty.");
  }

  @Test
  void aFewProductsForAQueryAreNamedWithTheirPrices() {
    stubSearch(
        "noise cancelling headphones",
        productSummary("AUD-SONY-WH1000XM5", "Sony WH-1000XM5", 37900, false, true),
        productSummary(
            "AUD-BOSE-QC-ULTRA", "Bose QuietComfort Ultra Headphones", 44900, true, false));
    var id = started();

    send(id, "I'm looking for noise cancelling headphones")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "I found 2 Products for \"noise cancelling headphones\": the Sony WH-1000XM5 at 379.00"
                + " EUR, in Stock; and the Bose QuietComfort Ultra Headphones from 449.00 EUR, out"
                + " of Stock.")
        .jsonPath("$.references[*].id")
        .isEqualTo(List.of("AUD-SONY-WH1000XM5", "AUD-BOSE-QC-ULTRA"));
    SEARCH_DISCOVERY.verify(0, getRequestedFor(urlPathEqualTo("/products/AUD-SONY-WH1000XM5")));
  }

  @Test
  void aStockQuestionNamingNoProductFoundListsWhatWasFound() {
    stubSearch(
        "headphones",
        productSummary(
            "AUD-BOSE-QC-ULTRA", "Bose QuietComfort Ultra Headphones", 44900, true, false));
    var id = started();

    send(id, "Do you have headphones in stock?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "I found 1 Product for \"headphones\": the Bose QuietComfort Ultra Headphones from"
                + " 449.00 EUR, out of Stock.");
  }

  @Test
  void aQueryNothingMatchesSaysSo() {
    stubSearch("unicorn");
    var id = started();

    send(id, "Do you sell a unicorn?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "I couldn't find a Product for \"unicorn\". Try other words, or browse the Catalog.")
        .jsonPath("$.references")
        .isEmpty();
  }

  @Test
  void aQuestionNamingNoProductAsksWhichOne() {
    var id = started();

    send(id, "What do you have in stock?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Which Product do you mean? Tell me its name, or what you're looking for, and I'll"
                + " look it up.");
    SEARCH_DISCOVERY.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void aSearchThatFailsSaysToTryAgain() {
    stubSearch("Pixel 9", serviceUnavailable());
    var id = started();

    send(id, "Is the Pixel 9 in stock?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("I couldn't look up our Products just now. Please try again in a moment.");
  }

  @Test
  void theWarrantyOnTheCustomersOwnOrderIsStillAboutReturns() {
    stubLatestOrderPaid();
    stubEligibility(ORDER_ID, okJson("{\"orderId\": \"%s\", \"lines\": []}".formatted(ORDER_ID)));
    var id = started();

    send(id, "Is my phone still under warranty?").expectStatus().isOk();

    SEARCH_DISCOVERY.verify(0, anyRequestedFor(anyUrl()));
    RETURNS_WARRANTY.verify(getRequestedFor(urlPathEqualTo("/rmas/eligibility")));
  }
}
