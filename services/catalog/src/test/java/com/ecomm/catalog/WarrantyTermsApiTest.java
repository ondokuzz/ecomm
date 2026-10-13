package com.ecomm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A Product's Warranty terms (months and manufacturer, or none) and Serial tracking ({@code NONE},
 * {@code SERIAL} or {@code IMEI}) are set by Staff and read by anyone.
 */
class WarrantyTermsApiTest extends CatalogApiTest {

  /** The Category these tests' Products belong to; a 409 means an earlier test created it. */
  @BeforeEach
  void tabletsCategory() {
    http.post()
        .uri("/categories")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"slug": "warranty-tablets", "name": "Tablets", "attributes": [
              {"name": "brand", "type": "TEXT", "required": true, "variantAxis": false}]}
            """)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isIn(201, 409));
  }

  @Test
  void staffCanGiveAProductWarrantyTermsAndSerialTracking() {
    var sku = newSku();

    create(
            tablet(
                sku,
                "\"warranty\": {\"months\": 24, \"manufacturer\": \"Samsung\"},"
                    + " \"serialTracking\": \"IMEI\","))
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.warranty.months")
        .isEqualTo(24)
        .jsonPath("$.serialTracking")
        .isEqualTo("IMEI");

    http.get()
        .uri("/products/{sku}", sku)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.warranty.months")
        .isEqualTo(24)
        .jsonPath("$.warranty.manufacturer")
        .isEqualTo("Samsung")
        .jsonPath("$.serialTracking")
        .isEqualTo("IMEI");
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "\"warranty\": {\"months\": 0, \"manufacturer\": \"Samsung\"},   | warranty.months",
        "\"warranty\": {\"months\": 121, \"manufacturer\": \"Samsung\"}, | warranty.months",
        "\"warranty\": {\"manufacturer\": \"Samsung\"},                  | warranty.months",
        "\"warranty\": {\"months\": 12},                                 | warranty.manufacturer",
        "\"warranty\": {\"months\": 12, \"manufacturer\": \"  \"},       | warranty.manufacturer",
        "\"serialTracking\": \"BARCODE\",                                | serialTracking",
      })
  void warrantyTermsOrSerialTrackingCatalogCantAcceptAreABadRequestNamingTheField(
      String fields, String field) {
    var sku = newSku();

    create(tablet(sku, fields))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors.length()")
        .isEqualTo(1)
        .jsonPath("$.errors[0].field")
        .isEqualTo(field);

    http.get().uri("/products/{sku}", sku).exchange().expectStatus().isNotFound();
  }

  @Test
  void everyOffendingFieldIsNamedAndAnUpdateIsValidatedToo() {
    var sku = newSku();
    create(tablet(sku, "")).expectStatus().isCreated();

    http.put()
        .uri("/products/{sku}", sku)
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            tablet(
                sku,
                "\"warranty\": {\"months\": 200, \"manufacturer\": \"\"},"
                    + " \"serialTracking\": \"imei\","))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[*].field")
        .isEqualTo(List.of("warranty.months", "warranty.manufacturer", "serialTracking"));

    http.get()
        .uri("/products/{sku}", sku)
        .exchange()
        .expectBody()
        .jsonPath("$.serialTracking")
        .isEqualTo("NONE");
  }

  @Test
  void anUpdateCanTakeWarrantyTermsAwayAndChangeSerialTracking() {
    var sku = newSku();
    create(
            tablet(
                sku,
                "\"warranty\": {\"months\": 24, \"manufacturer\": \"Samsung\"},"
                    + " \"serialTracking\": \"IMEI\","))
        .expectStatus()
        .isCreated();

    http.put()
        .uri("/products/{sku}", sku)
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(tablet(sku, "\"serialTracking\": \"SERIAL\","))
        .exchange()
        .expectStatus()
        .isOk();

    http.get()
        .uri("/products/{sku}", sku)
        .exchange()
        .expectBody()
        .jsonPath("$.warranty")
        .isEmpty()
        .jsonPath("$.serialTracking")
        .isEqualTo("SERIAL");
  }

  @Test
  void aProductSentWithoutEitherHasNoWarrantyAndNoSerialTracking() {
    var sku = newSku();

    create(tablet(sku, ""))
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.warranty")
        .isEmpty()
        .jsonPath("$.serialTracking")
        .isEqualTo("NONE");
  }

  static String newSku() {
    return "WTY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  /** A tablet with {@code fields} (each followed by a comma) added to its body. */
  static String tablet(String sku, String fields) {
    return """
        {"sku": "%s", "name": "Tablet", "category": "warranty-tablets", %s
         "attributes": {"brand": "Samsung"},
         "variants": [{"id": "%s", "price": {"amountMinor": 49900, "currency": "EUR"}}]}
        """
        .formatted(sku, fields, sku);
  }

  private RestTestClient.ResponseSpec create(String body) {
    return http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }
}
