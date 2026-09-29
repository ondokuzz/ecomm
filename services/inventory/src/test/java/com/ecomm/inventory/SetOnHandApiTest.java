package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Staff set how many units of a Variant are on hand, never below what Reservations hold. */
class SetOnHandApiTest extends InventoryApiTest {

  @Test
  void staffCreateStockForANewVariant() {
    var variant = "TEST-" + UUID.randomUUID();

    setOnHand(staffToken(), variant, onHandBody(12))
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.variantId")
        .isEqualTo(variant)
        .jsonPath("$.onHand")
        .isEqualTo(12)
        .jsonPath("$.reserved")
        .isEqualTo(0)
        .jsonPath("$.quantity")
        .isEqualTo(12);

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 12, 12, 0));
  }

  @Test
  void staffChangeAVariantsOnHandStock() {
    var variant = newVariant(12);
    reserved(item(variant, 2));

    setOnHand(staffToken(), variant, onHandBody(5))
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.onHand")
        .isEqualTo(5)
        .jsonPath("$.quantity")
        .isEqualTo(3);

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 3, 5, 2));
  }

  @Test
  void onHandCanGoDownToTheReservedQuantityButNoFurther() {
    var variant = newVariant(10);
    reserved(item(variant, 4));

    setOnHand(staffToken(), variant, onHandBody(3))
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reserved")
        .isEqualTo(4);
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 6, 10, 4));

    setOnHand(staffToken(), variant, onHandBody(4)).expectStatus().isOk();
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 0, 4, 4));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"onHand\": -1}", "{}", "{\"onHand\": 1.5}", "not json"})
  void anInvalidOnHandIsABadRequest(String body) {
    var variant = newVariant(10);

    setOnHand(staffToken(), variant, body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(stockOf(variant).onHand()).isEqualTo(10);
  }

  @Test
  void anOverlongVariantIdIsABadRequest() {
    setOnHand(staffToken(), "TEST-" + "X".repeat(60), onHandBody(1)).expectStatus().isBadRequest();
  }

  @Test
  void settingOnHandNeedsAToken() {
    var variant = newVariant(10);

    http.put()
        .uri("/stock/{variantId}", variant)
        .contentType(MediaType.APPLICATION_JSON)
        .body(onHandBody(1))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(stockOf(variant).onHand()).isEqualTo(10);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "CHECKOUT"})
  void onlyStaffCanSetOnHand(String role) {
    var variant = newVariant(10);

    setOnHand(FakeKeycloak.token("someone-" + role, role), variant, onHandBody(1))
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(stockOf(variant).onHand()).isEqualTo(10);
  }
}
