package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * Staff stop stocking a Variant, as when its Product leaves the Catalog, unless Reservations still
 * hold some of it. Its past Reservations stay.
 */
class RemoveStockApiTest extends InventoryApiTest {

  @Test
  void staffRemoveAVariantsStock() {
    var variant = newVariant(12);

    removeStock(staffToken(), variant).expectStatus().isNoContent();

    http.get().uri("/stock/{variantId}", variant).exchange().expectStatus().isNotFound();
  }

  @Test
  void aRemovedVariantCanNoLongerBeReservedButCanBeStockedAgain() {
    var variant = newVariant(12);
    removeStock(staffToken(), variant).expectStatus().isNoContent();

    reserve(reservationBody(CUSTOMER, inFifteenMinutes(), item(variant, 1)))
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.unknownVariants[0]")
        .isEqualTo(variant);

    setOnHand(staffToken(), variant, onHandBody(3)).expectStatus().isCreated();
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 3, 3, 0));
  }

  @Test
  void stockReservationsHoldCantBeRemoved() {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 4));

    removeStock(staffToken(), variant)
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reserved")
        .isEqualTo(4);
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 6, 10, 4));

    release(reservation.id()).expectStatus().isOk();
    removeStock(staffToken(), variant).expectStatus().isNoContent();
  }

  @Test
  void stockOnceReservedCanBeRemovedWhenNothingHoldsItAnyMore() {
    var committed = newVariant(10);
    commit(reserved(item(committed, 2)).id()).expectStatus().isOk();
    var expired = newVariant(10);
    reserved(clock.now().plusSeconds(60), item(expired, 2));
    clock.advanceBy(Duration.ofSeconds(60));

    removeStock(staffToken(), committed).expectStatus().isNoContent();
    removeStock(staffToken(), expired).expectStatus().isNoContent();
  }

  @Test
  void removingAVariantInventoryDoesntStockIsNotFound() {
    removeStock(staffToken(), "TEST-UNKNOWN")
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void removingStockNeedsAToken() {
    var variant = newVariant(10);

    http.delete()
        .uri("/stock/{variantId}", variant)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(stockOf(variant).onHand()).isEqualTo(10);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "CHECKOUT"})
  void onlyStaffCanRemoveStock(String role) {
    var variant = newVariant(10);

    removeStock(FakeKeycloak.token("someone-" + role, role), variant)
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(stockOf(variant).onHand()).isEqualTo(10);
  }
}
