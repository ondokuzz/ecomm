package com.ecomm.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Staff create, update and delete Products; nobody else may. */
class ManageProductsApiTest extends CatalogApiTest {

  /** The Category these tests' Products belong to; a 409 means an earlier test created it. */
  @BeforeEach
  void wearablesCategory() {
    http.post()
        .uri("/categories")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"slug": "wearables", "name": "Wearables", "attributes": [
              {"name": "brand", "type": "TEXT", "required": true, "variantAxis": false},
              {"name": "strap", "type": "ENUM", "values": ["silicone", "leather", "metal"],
               "required": true, "variantAxis": false},
              {"name": "waterResistance", "type": "NUMBER", "required": false,
               "variantAxis": false},
              {"name": "gps", "type": "BOOLEAN", "required": false, "variantAxis": false},
              {"name": "colour", "type": "TEXT", "required": true, "variantAxis": true}]}
            """)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isIn(201, 409));
  }

  private static String product(String sku, String name, long amountMinor) {
    return """
        {
          "sku": "%s",
          "name": "%s",
          "category": "wearables",
          "attributes": {"brand": "Garmin", "strap": "silicone"},
          "images": ["/images/products/%s/front.svg"],
          "variants": [{"id": "%s", "axisValues": {"colour": "Black"},
                        "price": {"amountMinor": %d, "currency": "EUR"}}]
        }
        """
        .formatted(sku, name, sku.toLowerCase(), sku, amountMinor);
  }

  @Test
  void staffCanCreateAProductThatAnyoneCanThenView() {
    http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(product("WRB-FENIX-8", "Garmin Fenix 8", 99900))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectHeader()
        .location("/products/WRB-FENIX-8")
        .expectBody()
        .jsonPath("$.variants[0].id")
        .isEqualTo("WRB-FENIX-8");

    http.get()
        .uri("/products?category=wearables")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[?(@.sku == 'WRB-FENIX-8')].name")
        .isEqualTo("Garmin Fenix 8");
  }

  @Test
  void creatingAProductWithATakenSkuIsAConflict() {
    create("WRB-VENU-3", "Garmin Venu 3", 44900);

    http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(product("WRB-VENU-3", "Another Venu", 1))
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    http.get()
        .uri("/products/WRB-VENU-3")
        .exchange()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Garmin Venu 3");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {"sku": "WRB-BAD", "category": "wearables", "variants": [{"id": "WRB-BAD",
         "axisValues": {"colour": "Black"}, "price": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"sku": "WRB-BAD", "name": "No price", "category": "wearables", "variants": [{"id": "WRB-BAD",
         "axisValues": {"colour": "Black"}}]}
        """,
        """
        {"sku": "WRB-BAD", "name": "Negative", "category": "wearables", "variants": [{"id": "WRB-BAD",
         "axisValues": {"colour": "Black"}, "price": {"amountMinor": -1, "currency": "EUR"}}]}
        """,
        """
        {"sku": "WRB-BAD", "name": "Bad currency", "category": "wearables", "variants": [{"id": "WRB-BAD",
         "axisValues": {"colour": "Black"}, "price": {"amountMinor": 100, "currency": "EURO"}}]}
        """,
        """
        {"sku": "WRB-BAD", "name": "No Variant ID", "category": "wearables", "variants": [{
         "axisValues": {"colour": "Black"}, "price": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"sku": "WRB-BAD", "name": "Bad category", "category": "Smart Watches", "variants": [{"id": "WRB-BAD",
         "axisValues": {"colour": "Black"}, "price": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        "not json"
      })
  void anInvalidProductIsABadRequest(String body) {
    http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    http.get().uri("/products/WRB-BAD").exchange().expectStatus().isNotFound();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{\"brand\": \"Garmin\"}                                      | attributes.strap",
        "{\"brand\": \"Garmin\", \"strap\": \"nylon\"}                 | attributes.strap",
        "{\"brand\": \"Garmin\", \"strap\": \"metal\", \"gps\": \"yes\"}  | attributes.gps",
        "{\"brand\": \"Garmin\", \"strap\": \"metal\", \"waterResistance\": \"deep\"}"
            + " | attributes.waterResistance",
        "{\"brand\": \"Garmin\", \"strap\": \"metal\", \"heartRate\": \"yes\"}"
            + " | attributes.heartRate",
      })
  void attributesBreakingTheCategorysDefinitionsAreABadRequestNamingTheField(
      String attributes, String field) {
    http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"sku": "WRB-INVALID", "name": "Invalid", "category": "wearables", "attributes": %s,
             "variants": [{"id": "WRB-INVALID", "axisValues": {"colour": "Black"},
              "price": {"amountMinor": 100, "currency": "EUR"}}]}
            """
                .formatted(attributes))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.errors.length()")
        .isEqualTo(1)
        .jsonPath("$.errors[0].field")
        .isEqualTo(field)
        .jsonPath("$.errors[0].message")
        .exists();

    http.get().uri("/products/WRB-INVALID").exchange().expectStatus().isNotFound();
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{\"amountMinor\": 100, \"currency\": \"EURO\"} | variants[1].price.currency",
        "{\"amountMinor\": 100, \"currency\": \"XXX\"}  | variants[1].price.currency",
        "{\"amountMinor\": 100, \"currency\": \"XAU\"}  | variants[1].price.currency",
        "{\"amountMinor\": 100}                         | variants[1].price",
        "null                                         | variants[1].price",
      })
  void aPriceCatalogCantReadIsABadRequestNamingTheField(String price, String field) {
    http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"sku": "WRB-PRICED", "name": "Priced", "category": "wearables",
             "attributes": {"brand": "Garmin", "strap": "metal"},
             "variants": [
               {"id": "WRB-PRICED", "axisValues": {"colour": "Black"},
                "price": {"amountMinor": 100, "currency": "EUR"}},
               {"id": "WRB-PRICED-WHITE", "axisValues": {"colour": "White"}, "price": %s}]}
            """
                .formatted(price))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors.length()")
        .isEqualTo(1)
        .jsonPath("$.errors[0].field")
        .isEqualTo(field);

    http.get().uri("/products/WRB-PRICED").exchange().expectStatus().isNotFound();
  }

  @Test
  void everyOffendingAttributeIsNamed() {
    http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"sku": "WRB-INVALID", "name": "Invalid", "category": "wearables",
             "attributes": {"strap": "nylon", "gps": "maybe"},
             "variants": [{"id": "WRB-INVALID", "axisValues": {"colour": "Black"},
              "price": {"amountMinor": 100, "currency": "EUR"}}]}
            """)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[*].field")
        .isEqualTo(List.of("attributes.brand", "attributes.strap", "attributes.gps"));
  }

  @Test
  void aProductInACategoryThatDoesNotExistIsABadRequest() {
    http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"sku": "GRD-BENCH", "name": "Bench", "category": "garden-furniture",
             "variants": [{"id": "GRD-BENCH", "price": {"amountMinor": 100, "currency": "EUR"}}]}
            """)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("category");

    http.get().uri("/products/GRD-BENCH").exchange().expectStatus().isNotFound();
  }

  @Test
  void updatingIsValidatedToo() {
    create("WRB-EPIX-2", "Garmin Epix 2", 59900);

    http.put()
        .uri("/products/WRB-EPIX-2")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"name": "Garmin Epix 2", "category": "wearables", "attributes": {"brand": "Garmin"},
             "variants": [{"id": "WRB-EPIX-2", "axisValues": {"colour": "Black"},
                           "price": {"amountMinor": 59900, "currency": "EUR"}}]}
            """)
        .exchange()
        .expectStatus()
        .isBadRequest();

    http.get()
        .uri("/products/WRB-EPIX-2")
        .exchange()
        .expectBody()
        .jsonPath("$.attributes.strap")
        .isEqualTo("silicone");
  }

  @Test
  void staffCanUpdateAProduct() {
    create("WRB-FORERUNNER-265", "Garmin Forerunner 265", 44900);

    http.put()
        .uri("/products/WRB-FORERUNNER-265")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(product("WRB-FORERUNNER-265", "Garmin Forerunner 265S", 39900))
        .exchange()
        .expectStatus()
        .isOk();

    http.get()
        .uri("/products/WRB-FORERUNNER-265")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Garmin Forerunner 265S")
        .jsonPath("$.priceFrom.amountMinor")
        .isEqualTo(39900)
        .jsonPath("$.variants[0].price.amountMinor")
        .isEqualTo(39900);
  }

  @Test
  void updatingAMissingProductIsNotFound() {
    http.put()
        .uri("/products/WRB-NEVER-CREATED")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(product("WRB-NEVER-CREATED", "Ghost", 100))
        .exchange()
        .expectStatus()
        .isNotFound();

    http.get().uri("/products/WRB-NEVER-CREATED").exchange().expectStatus().isNotFound();
  }

  @Test
  void updatingWithADifferentSkuInTheBodyIsABadRequest() {
    create("WRB-INSTINCT-2", "Garmin Instinct 2", 29900);

    http.put()
        .uri("/products/WRB-INSTINCT-2")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(product("WRB-SOMETHING-ELSE", "Renamed", 100))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void staffCanDeleteAProduct() {
    create("WRB-LILY-2", "Garmin Lily 2", 24900);

    http.delete()
        .uri("/products/WRB-LILY-2")
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isNoContent();

    http.get().uri("/products/WRB-LILY-2").exchange().expectStatus().isNotFound();
  }

  @Test
  void deletingAMissingProductIsNotFound() {
    http.delete()
        .uri("/products/WRB-NEVER-CREATED")
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"POST /products", "PUT /products/PHN-PIXEL-9", "DELETE /products/PHN-PIXEL-9"})
  void aCustomerIsForbiddenFromChangingProducts(String request) {
    send(request, customerToken())
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    http.get().uri("/products/PHN-PIXEL-9").exchange().expectStatus().isOk();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"POST /products", "PUT /products/PHN-PIXEL-9", "DELETE /products/PHN-PIXEL-9"})
  void changingProductsNeedsAToken(String request) {
    send(request, null)
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    http.get().uri("/products/PHN-PIXEL-9").exchange().expectStatus().isOk();
  }

  private RestTestClient.ResponseSpec send(String request, String token) {
    var parts = request.split(" ");
    return http.method(HttpMethod.valueOf(parts[0]))
        .uri(parts[1])
        .headers(
            h -> {
              if (token != null) {
                h.setBearerAuth(token);
              }
            })
        .contentType(MediaType.APPLICATION_JSON)
        .body(product("PHN-PIXEL-9", "Hijacked", 1))
        .exchange();
  }

  private void create(String sku, String name, long amountMinor) {
    http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(product(sku, name, amountMinor))
        .exchange()
        .expectStatus()
        .isCreated();
  }
}
