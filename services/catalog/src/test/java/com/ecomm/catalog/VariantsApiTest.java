package com.ecomm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A Product's Variants: each covers exactly its Category's Variant axes, no two share axis values,
 * and a Variant ID belongs to one Product in the whole Catalog. Anyone can look a Variant up by ID.
 */
class VariantsApiTest extends CatalogApiTest {

  /** The Category these tests' Products belong to; a 409 means an earlier test created it. */
  @BeforeEach
  void eReadersCategory() {
    http.post()
        .uri("/categories")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"slug": "e-readers", "name": "E-readers", "attributes": [
              {"name": "brand", "type": "TEXT", "required": true, "variantAxis": false},
              {"name": "colour", "type": "TEXT", "required": true, "variantAxis": true},
              {"name": "storage", "type": "ENUM", "values": ["128 GB", "256 GB"],
               "required": true, "variantAxis": true}]}
            """)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isIn(201, 409));
  }

  private static String tablet(String sku, String variants) {
    return """
        {"sku": "%s", "name": "Tablet %s", "category": "e-readers",
         "attributes": {"brand": "Acme"}, "images": ["/images/products/%s/front.svg"],
         "variants": %s}
        """
        .formatted(sku, sku, sku.toLowerCase(), variants);
  }

  private static String variant(String id, String colour, String storage, long amountMinor) {
    return """
        {"id": "%s", "axisValues": {"colour": "%s", "storage": "%s"},
         "price": {"amountMinor": %d, "currency": "EUR"}}
        """
        .formatted(id, colour, storage, amountMinor);
  }

  @Test
  void aProductListsEachVariantWithItsAxisValuesPriceAndImages() {
    create(
            tablet(
                "TAB-ONE",
                "[%s, %s]"
                    .formatted(
                        variant("TAB-ONE-GREY-128", "Grey", "128 GB", 49900),
                        """
                    {"id": "TAB-ONE-PINK-256", "axisValues": {"storage": "256 GB", "colour": "Pink"},
                     "price": {"amountMinor": 59900, "currency": "EUR"},
                     "images": ["/images/products/tab-one/pink.svg"]}
                    """)))
        .expectStatus()
        .isCreated();

    http.get()
        .uri("/products/TAB-ONE")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.variants[*].id")
        .isEqualTo(List.of("TAB-ONE-GREY-128", "TAB-ONE-PINK-256"))
        .jsonPath("$.variants[1].axisValues")
        .isEqualTo(Map.of("colour", "Pink", "storage", "256 GB"))
        .jsonPath("$.variants[1].price.amountMinor")
        .isEqualTo(59900)
        .jsonPath("$.variants[1].images")
        .isEqualTo(List.of("/images/products/tab-one/pink.svg"))
        .jsonPath("$.variants[0].images")
        .isEqualTo(List.of())
        .jsonPath("$.price")
        .doesNotExist();
  }

  @Test
  void aProductNeedsAtLeastOneVariant() {
    create(tablet("TAB-NONE", "[]")).expectStatus().isBadRequest();
    create(
            """
            {"sku": "TAB-NONE", "name": "Tablet", "category": "e-readers",
             "attributes": {"brand": "Acme"}}
            """)
        .expectStatus()
        .isBadRequest();

    http.get().uri("/products/TAB-NONE").exchange().expectStatus().isNotFound();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{\"colour\": \"Grey\"}                                      | variants[0].axisValues.storage",
        "{\"colour\": \"Grey\", \"storage\": \"\"}                   | variants[0].axisValues.storage",
        "{\"colour\": \"Grey\", \"storage\": \"9 TB\"}               | variants[0].axisValues.storage",
        "{\"colour\": \"Grey\", \"storage\": \"128 GB\", \"brand\": \"Acme\"}"
            + " | variants[0].axisValues.brand",
        "{\"colour\": \"Grey\", \"storage\": \"128 GB\", \"wifi\": \"6E\"}"
            + " | variants[0].axisValues.wifi",
      })
  void axisValuesMustCoverExactlyTheCategorysAxes(String axisValues, String field) {
    create(
            tablet(
                "TAB-AXES",
                """
                [{"id": "TAB-AXES-1", "axisValues": %s,
                  "price": {"amountMinor": 100, "currency": "EUR"}}]
                """
                    .formatted(axisValues)))
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.errors[*].field")
        .isEqualTo(List.of(field));

    http.get().uri("/products/TAB-AXES").exchange().expectStatus().isNotFound();
  }

  @Test
  void anAxisValueOnTheProductItselfIsABadRequest() {
    create(
            """
            {"sku": "TAB-AXIS-ATTR", "name": "Tablet", "category": "e-readers",
             "attributes": {"brand": "Acme", "colour": "Grey"}, "variants": [%s]}
            """
                .formatted(variant("TAB-AXIS-ATTR-1", "Grey", "128 GB", 100)))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[*].field")
        .isEqualTo(List.of("attributes.colour"));
  }

  @Test
  void twoVariantsWithTheSameAxisValuesAreABadRequest() {
    create(
            tablet(
                "TAB-TWINS",
                "[%s, %s, %s]"
                    .formatted(
                        variant("TAB-TWINS-1", "Grey", "128 GB", 100),
                        variant("TAB-TWINS-2", "Grey", "256 GB", 200),
                        variant("TAB-TWINS-3", "Grey", "128 GB", 300))))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[*].field")
        .isEqualTo(List.of("variants[2].axisValues"))
        .jsonPath("$.errors[0].message")
        .value(message -> assertThat((String) message).contains("variants[0]"));

    http.get().uri("/products/TAB-TWINS").exchange().expectStatus().isNotFound();
  }

  @Test
  void aVariantIdListedTwiceInOneProductIsABadRequest() {
    create(
            tablet(
                "TAB-SAME-ID",
                "[%s, %s]"
                    .formatted(
                        variant("TAB-SAME-ID-1", "Grey", "128 GB", 100),
                        variant("TAB-SAME-ID-1", "Grey", "256 GB", 200))))
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void aProductsVariantsShareOneCurrency() {
    create(
            tablet(
                "TAB-MIXED",
                """
                [%s, {"id": "TAB-MIXED-2", "axisValues": {"colour": "Grey", "storage": "256 GB"},
                      "price": {"amountMinor": 100, "currency": "USD"}}]
                """
                    .formatted(variant("TAB-MIXED-1", "Grey", "128 GB", 100))))
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void aVariantIdAnotherProductUsesIsAConflict() {
    create(tablet("TAB-FIRST", "[%s]".formatted(variant("TAB-SHARED", "Grey", "128 GB", 100))))
        .expectStatus()
        .isCreated();

    create(tablet("TAB-SECOND", "[%s]".formatted(variant("TAB-SHARED", "Pink", "128 GB", 100))))
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.detail")
        .value(detail -> assertThat((String) detail).contains("TAB-SHARED"));
    http.get().uri("/products/TAB-SECOND").exchange().expectStatus().isNotFound();

    // Taking it over on update is refused too, but a Product keeps its own Variant IDs.
    create(tablet("TAB-THIRD", "[%s]".formatted(variant("TAB-THIRD-1", "Grey", "128 GB", 100))))
        .expectStatus()
        .isCreated();
    replace(
            "TAB-THIRD",
            tablet(
                "TAB-THIRD",
                "[%s, %s]"
                    .formatted(
                        variant("TAB-THIRD-1", "Grey", "128 GB", 100),
                        variant("TAB-SHARED", "Pink", "128 GB", 1))))
        .expectStatus()
        .isEqualTo(409);
    replace(
            "TAB-FIRST",
            tablet("TAB-FIRST", "[%s]".formatted(variant("TAB-SHARED", "Grey", "128 GB", 1))))
        .expectStatus()
        .isOk();
  }

  @Test
  void anUpdateMayAddVariantsButNeverDropOrRenameOne() {
    create(
            tablet(
                "TAB-KEEP",
                "[%s, %s]"
                    .formatted(
                        variant("TAB-KEEP-GREY", "Grey", "128 GB", 100),
                        variant("TAB-KEEP-PINK", "Pink", "128 GB", 100))))
        .expectStatus()
        .isCreated();

    // Renaming TAB-KEEP-PINK drops its ID just as leaving it out does.
    replace(
            "TAB-KEEP",
            tablet(
                "TAB-KEEP",
                "[%s, %s]"
                    .formatted(
                        variant("TAB-KEEP-GREY", "Grey", "128 GB", 100),
                        variant("TAB-KEEP-ROSE", "Pink", "128 GB", 100))))
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.detail")
        .value(detail -> assertThat((String) detail).contains("TAB-KEEP-PINK"));
    replace(
            "TAB-KEEP",
            tablet("TAB-KEEP", "[%s]".formatted(variant("TAB-KEEP-GREY", "Grey", "128 GB", 100))))
        .expectStatus()
        .isEqualTo(409);
    http.get()
        .uri("/products/TAB-KEEP")
        .exchange()
        .expectBody()
        .jsonPath("$.variants[*].id")
        .isEqualTo(List.of("TAB-KEEP-GREY", "TAB-KEEP-PINK"));

    replace(
            "TAB-KEEP",
            tablet(
                "TAB-KEEP",
                "[%s, %s, %s]"
                    .formatted(
                        variant("TAB-KEEP-GREY", "Grey", "128 GB", 90),
                        variant("TAB-KEEP-PINK", "Pink", "128 GB", 100),
                        variant("TAB-KEEP-PINK-256", "Pink", "256 GB", 120))))
        .expectStatus()
        .isOk();
  }

  @Test
  void aDeletedProductsVariantIdsAreFreeAgain() {
    create(tablet("TAB-OLD", "[%s]".formatted(variant("TAB-REUSED", "Grey", "128 GB", 100))))
        .expectStatus()
        .isCreated();
    http.delete()
        .uri("/products/TAB-OLD")
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isNoContent();

    create(tablet("TAB-NEW", "[%s]".formatted(variant("TAB-REUSED", "Grey", "128 GB", 100))))
        .expectStatus()
        .isCreated();
  }

  @Test
  void anyoneCanLookUpAVariantWithItsProductsSkuNameCategoryAndImages() {
    create(
            tablet(
                "TAB-LOOKUP",
                "[%s, %s]"
                    .formatted(
                        variant("TAB-LOOKUP-GREY", "Grey", "128 GB", 49900),
                        variant("TAB-LOOKUP-PINK", "Pink", "256 GB", 59900))))
        .expectStatus()
        .isCreated();

    http.get()
        .uri("/variants/{id}", "TAB-LOOKUP-PINK")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo("TAB-LOOKUP-PINK")
        .jsonPath("$.price.amountMinor")
        .isEqualTo(59900)
        .jsonPath("$.price.currency")
        .isEqualTo("EUR")
        .jsonPath("$.axisValues")
        .isEqualTo(Map.of("colour", "Pink", "storage", "256 GB"))
        .jsonPath("$.images")
        .isEqualTo(List.of())
        .jsonPath("$.product.sku")
        .isEqualTo("TAB-LOOKUP")
        .jsonPath("$.product.name")
        .isEqualTo("Tablet TAB-LOOKUP")
        .jsonPath("$.product.category")
        .isEqualTo("e-readers")
        .jsonPath("$.product.images")
        .isEqualTo(List.of("/images/products/tab-lookup/front.svg"));
  }

  @Test
  void aVariantsAxisValuesFollowTheCategorysAxisOrder() {
    create(
            tablet(
                "TAB-ORDER",
                """
                [{"id": "TAB-ORDER-1", "axisValues": {"storage": "128 GB", "colour": "Grey"},
                  "price": {"amountMinor": 100, "currency": "EUR"}}]
                """))
        .expectStatus()
        .isCreated();

    var body =
        http.get()
            .uri("/variants/{id}", "TAB-ORDER-1")
            .exchange()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    assertThat(body).containsSubsequence("\"colour\"", "\"storage\"");
  }

  @Test
  void anUnknownVariantIsNotFound() {
    http.get()
        .uri("/variants/{id}", "NO-SUCH-VARIANT")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void aProductsPriceFromIsItsLowestVariantPrice() {
    create(
            tablet(
                "TAB-FROM",
                "[%s, %s, %s]"
                    .formatted(
                        variant("TAB-FROM-1", "Grey", "256 GB", 59900),
                        variant("TAB-FROM-2", "Grey", "128 GB", 44900),
                        variant("TAB-FROM-3", "Pink", "128 GB", 49900))))
        .expectStatus()
        .isCreated();

    http.get()
        .uri("/products/TAB-FROM")
        .exchange()
        .expectBody()
        .jsonPath("$.priceFrom.amountMinor")
        .isEqualTo(44900)
        .jsonPath("$.priceFrom.currency")
        .isEqualTo("EUR");
    http.get()
        .uri("/products?category=e-readers")
        .exchange()
        .expectBody()
        .jsonPath("$[?(@.sku == 'TAB-FROM')].priceFrom.amountMinor")
        .isEqualTo(44900);
  }

  @ParameterizedTest
  @CsvSource({
    "PHN-PIXEL-9, PHN-PIXEL-9, 3",
    "PHN-IPHONE-16, PHN-IPHONE-16, 4",
    "LPT-MBA-13-M3, LPT-MBA-13-M3, 3",
  })
  void someSeedProductsComeInSeveralVariantsTheFirstKeepingTheSku(
      String sku, String firstVariantId, int variants) {
    http.get()
        .uri("/products/{sku}", sku)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.variants.length()")
        .isEqualTo(variants)
        .jsonPath("$.variants[0].id")
        .isEqualTo(firstVariantId);
  }

  @Test
  void aSeedVariantCanBeLookedUpAndPricedOnItsOwn() {
    http.get()
        .uri("/variants/{id}", "PHN-PIXEL-9-OBSIDIAN-256")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.product.sku")
        .isEqualTo("PHN-PIXEL-9")
        .jsonPath("$.axisValues")
        .isEqualTo(Map.of("color", "Obsidian", "storage", "256 GB"))
        .jsonPath("$.price.amountMinor")
        .isEqualTo(89900);
    http.get()
        .uri("/products/{sku}", "PHN-PIXEL-9")
        .exchange()
        .expectBody()
        .jsonPath("$.priceFrom.amountMinor")
        .isEqualTo(79900);
  }

  private RestTestClient.ResponseSpec create(String body) {
    return http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  private RestTestClient.ResponseSpec replace(String sku, String body) {
    return http.put()
        .uri("/products/{sku}", sku)
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }
}
