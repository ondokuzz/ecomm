package com.ecomm.catalog;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Staff create, change and delete Categories; nobody else may. */
class ManageCategoriesApiTest extends CatalogApiTest {

  private static String category(String slug, String name) {
    return """
        {
          "slug": "%s",
          "name": "%s",
          "attributes": [
            {"name": "brand", "type": "TEXT", "required": true, "variantAxis": false},
            {"name": "lens", "type": "ENUM", "values": ["Wide", "Tele"], "required": false,
             "variantAxis": false},
            {"name": "colour", "type": "TEXT", "required": true, "variantAxis": true}
          ]
        }
        """
        .formatted(slug, name);
  }

  @Test
  void staffCanCreateACategoryThatAnyoneCanThenView() {
    http.post()
        .uri("/categories")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(category("cameras", "Cameras"))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectHeader()
        .location("/categories/cameras");

    http.get()
        .uri("/categories/cameras")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.slug")
        .isEqualTo("cameras")
        .jsonPath("$.name")
        .isEqualTo("Cameras")
        .jsonPath("$.productCount")
        .isEqualTo(0)
        .jsonPath("$.attributes[*].name")
        .isEqualTo(List.of("brand", "lens", "colour"))
        .jsonPath("$.attributes[1].type")
        .isEqualTo("ENUM")
        .jsonPath("$.attributes[1].values")
        .isEqualTo(List.of("Wide", "Tele"))
        .jsonPath("$.attributes[2].variantAxis")
        .isEqualTo(true);

    http.get()
        .uri("/categories")
        .exchange()
        .expectBody()
        .jsonPath("$[?(@.slug == 'cameras')].name")
        .isEqualTo("Cameras");
  }

  @Test
  void creatingACategoryWithATakenSlugIsAConflict() {
    create("drones", "Drones");

    http.post()
        .uri("/categories")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(category("drones", "Other drones"))
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    http.get()
        .uri("/categories/drones")
        .exchange()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Drones");
  }

  @Test
  void aMissingCategoryIsNotFound() {
    http.get()
        .uri("/categories/garden-furniture")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {"slug": "Smart Watches", "name": "Smart watches"}
        """,
        """
        {"slug": "bad-no-name"}
        """,
        """
        {"slug": "bad-enum", "name": "Bad", "attributes": [
          {"name": "size", "type": "ENUM", "values": [], "required": true, "variantAxis": false}]}
        """,
        """
        {"slug": "bad-type", "name": "Bad", "attributes": [
          {"name": "size", "type": "COLOUR", "required": true, "variantAxis": false}]}
        """,
        """
        {"slug": "bad-twice", "name": "Bad", "attributes": [
          {"name": "size", "type": "TEXT", "required": true, "variantAxis": false},
          {"name": "size", "type": "NUMBER", "required": false, "variantAxis": false}]}
        """,
        "not json"
      })
  void anInvalidCategoryIsABadRequest(String body) {
    http.post()
        .uri("/categories")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void staffCanChangeACategorysNameAndDefinitions() {
    create("tablets", "Tablets");

    http.put()
        .uri("/categories/tablets")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"name": "Tablets & e-readers", "attributes": [
              {"name": "brand", "type": "TEXT", "required": true, "variantAxis": false}]}
            """)
        .exchange()
        .expectStatus()
        .isOk();

    http.get()
        .uri("/categories/tablets")
        .exchange()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Tablets & e-readers")
        .jsonPath("$.attributes.length()")
        .isEqualTo(1);
  }

  @Test
  void changingACategoryWithADifferentSlugInTheBodyIsABadRequest() {
    create("monitors", "Monitors");

    http.put()
        .uri("/categories/monitors")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(category("screens", "Screens"))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void changingAMissingCategoryIsNotFound() {
    http.put()
        .uri("/categories/never-created")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(category("never-created", "Ghost"))
        .exchange()
        .expectStatus()
        .isNotFound();

    http.get().uri("/categories/never-created").exchange().expectStatus().isNotFound();
  }

  @Test
  void changingDefinitionsLeavesExistingProductsAloneUntilTheirNextWrite() {
    create("speakers", "Speakers");
    var speaker =
        """
        {"sku": "SPK-DEMO", "name": "Demo speaker", "category": "speakers",
         "attributes": {"brand": "Demo"}, "price": {"amountMinor": 100, "currency": "EUR"}}
        """;
    http.post()
        .uri("/products")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(speaker)
        .exchange()
        .expectStatus()
        .isCreated();

    http.put()
        .uri("/categories/speakers")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"name": "Speakers", "attributes": [
              {"name": "brand", "type": "TEXT", "required": true, "variantAxis": false},
              {"name": "watts", "type": "NUMBER", "required": true, "variantAxis": false}]}
            """)
        .exchange()
        .expectStatus()
        .isOk();

    http.get()
        .uri("/products/SPK-DEMO")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.attributes.brand")
        .isEqualTo("Demo")
        .jsonPath("$.attributes.watts")
        .doesNotExist();

    http.put()
        .uri("/products/SPK-DEMO")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(speaker)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("attributes.watts");
  }

  @Test
  void staffCanDeleteAnEmptyCategory() {
    create("printers", "Printers");

    http.delete()
        .uri("/categories/printers")
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isNoContent();

    http.get().uri("/categories/printers").exchange().expectStatus().isNotFound();
  }

  @Test
  void deletingACategoryThatStillHasProductsIsAConflict() {
    http.delete()
        .uri("/categories/phones")
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    http.get().uri("/categories/phones").exchange().expectStatus().isOk();
  }

  @Test
  void deletingAMissingCategoryIsNotFound() {
    http.delete()
        .uri("/categories/never-created")
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST /categories", "PUT /categories/audio", "DELETE /categories/audio"})
  void aCustomerIsForbiddenFromChangingCategories(String request) {
    send(request, customerToken())
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    http.get()
        .uri("/categories/audio")
        .exchange()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Audio");
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST /categories", "PUT /categories/audio", "DELETE /categories/audio"})
  void changingCategoriesNeedsAToken(String request) {
    send(request, null)
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    http.get()
        .uri("/categories/audio")
        .exchange()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Audio");
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
        .body(category("audio", "Hijacked"))
        .exchange();
  }

  private void create(String slug, String name) {
    http.post()
        .uri("/categories")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(category(slug, name))
        .exchange()
        .expectStatus()
        .isCreated();
  }
}
