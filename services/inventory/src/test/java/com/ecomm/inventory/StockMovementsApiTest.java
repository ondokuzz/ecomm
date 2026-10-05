package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import com.ecomm.inventory.application.port.in.ReleaseExpiredReservationsUseCase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Every change to a Variant's On-hand units, or to what Reservations hold of them, records Stock
 * movements, which Staff read newest first beside whether On-hand still equals their sum.
 */
class StockMovementsApiTest extends InventoryApiTest {

  @Autowired ReleaseExpiredReservationsUseCase sweeper;

  @Test
  void settingOnHandRecordsTheDifferenceAsAdjustedWithItsReason() {
    var variant = newVariant(10);
    setOnHand(staffToken(), variant, "{\"onHand\": 7, \"reason\": \"stocktake\"}")
        .expectStatus()
        .isOk();

    assertThat(ledgerOf(variant).changes())
        .containsExactly(
            movement("ADJUSTED", -3, 0, null, "stocktake"),
            movement("ADJUSTED", 10, 0, null, null));
  }

  @Test
  void settingOnHandToWhatItIsRecordsNothing() {
    var variant = newVariant(10);

    setOnHand(staffToken(), variant, onHandBody(10)).expectStatus().isOk();

    assertThat(ledgerOf(variant).changes()).hasSize(1);
  }

  @Test
  void reservingRecordsReservedForEachVariant() {
    var pixel = newVariant(10);
    var buds = newVariant(5);

    var reservation = reserved(item(pixel, 2), item(buds, 1));

    assertThat(ledgerOf(pixel).changes().getFirst())
        .isEqualTo(movement("RESERVED", 0, 2, reservation.id(), null));
    assertThat(ledgerOf(buds).changes().getFirst())
        .isEqualTo(movement("RESERVED", 0, 1, reservation.id(), null));
  }

  @Test
  void aFailedReservationRecordsNothing() {
    var plenty = newVariant(10);
    var scarce = newVariant(1);

    reserve(reservationBody(CUSTOMER, inFifteenMinutes(), item(plenty, 1), item(scarce, 2)))
        .expectStatus()
        .isEqualTo(409);

    assertThat(ledgerOf(plenty).changes()).hasSize(1);
    assertThat(ledgerOf(scarce).changes()).hasSize(1);
  }

  @Test
  void releasingRecordsReleasedOnce() {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 3));

    release(reservation.id()).expectStatus().isOk();
    release(reservation.id()).expectStatus().isOk();

    assertThat(ledgerOf(variant).changes())
        .startsWith(
            movement("RELEASED", 0, -3, reservation.id(), null),
            movement("RESERVED", 0, 3, reservation.id(), null))
        .hasSize(3);
  }

  @Test
  void committingRecordsCommittedOnce() {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 3));

    commit(reservation.id()).expectStatus().isOk();
    commit(reservation.id()).expectStatus().isOk();

    assertThat(ledgerOf(variant).changes())
        .startsWith(
            movement("COMMITTED", -3, -3, reservation.id(), null),
            movement("RESERVED", 0, 3, reservation.id(), null))
        .hasSize(3);
  }

  @Test
  void theSweepRecordsReleasedForAnExpiredReservation() {
    var variant = newVariant(10);
    var reservation = reserved(clock.now().plusSeconds(60), item(variant, 4));
    clock.advanceBy(Duration.ofSeconds(60));

    sweeper.releaseExpired();

    assertThat(ledgerOf(variant).changes().getFirst())
        .isEqualTo(movement("RELEASED", 0, -4, reservation.id(), null));
  }

  @Test
  void onHandEqualsTheSumOfItsMovementsAfterAMixOfChanges() {
    var variant = newVariant(20);
    commit(reserved(item(variant, 3)).id()).expectStatus().isOk();
    release(reserved(item(variant, 2)).id()).expectStatus().isOk();
    setOnHand(staffToken(), variant, onHandBody(25)).expectStatus().isOk();
    reserved(clock.now().plusSeconds(60), item(variant, 5));
    commit(reserved(item(variant, 4)).id()).expectStatus().isOk();
    clock.advanceBy(Duration.ofSeconds(60));
    sweeper.releaseExpired();
    reserved(item(variant, 1));

    var ledger = ledgerOf(variant);

    assertThat(ledger.onHand()).isEqualTo(21);
    assertThat(ledger.onHandFromMovements()).isEqualTo(21);
    assertThat(ledger.balanced()).isTrue();
    assertThat(ledger.total()).isEqualTo(11);
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 20, 21, 1));
  }

  @Test
  void stoppingAndStartingToStockAVariantKeepsItsLedgerBalanced() {
    var variant = newVariant(6);

    removeStock(staffToken(), variant).expectStatus().isNoContent();
    setOnHand(staffToken(), variant, onHandBody(4)).expectStatus().isCreated();

    var ledger = ledgerOf(variant);
    assertThat(ledger.changes())
        .containsExactly(
            movement("ADJUSTED", 4, 0, null, null),
            movement("ADJUSTED", -6, 0, null, "stopped stocking"),
            movement("ADJUSTED", 6, 0, null, null));
    assertThat(ledger.balanced()).isTrue();
  }

  @Test
  void movementsComeNewestFirstAPageAtATime() {
    var variant = newVariant(10);
    for (var onHand : List.of(12, 15, 19, 24)) {
      setOnHand(staffToken(), variant, onHandBody(onHand)).expectStatus().isOk();
    }

    var second = movements(staffToken(), variant, "?page=1&size=2").expectStatus().isOk();

    var ledger = second.expectBody(LedgerView.class).returnResult().getResponseBody();
    assertThat(ledger.items()).extracting(MovementView::onHandChange).containsExactly(3, 2);
    assertThat(ledger.page()).isEqualTo(1);
    assertThat(ledger.size()).isEqualTo(2);
    assertThat(ledger.total()).isEqualTo(5);
  }

  @Test
  void eachMovementSaysWhenItHappened() {
    var variant = newVariant(1);
    var at = ledgerOf(variant).items().getFirst().at();

    assertThat(Instant.parse(at)).isEqualTo(clock.now());
  }

  @Test
  void anUnknownVariantIsNotFound() {
    movements(staffToken(), "NO-SUCH-VARIANT", "")
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @ParameterizedTest
  @ValueSource(strings = {"?page=-1", "?size=0", "?size=101"})
  void aPageThatCantExistIsABadRequest(String query) {
    movements(staffToken(), "TEST-AUTH", query).expectStatus().isBadRequest();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "CHECKOUT"})
  void onlyStaffReadMovements(String role) {
    movements(FakeKeycloak.token("someone", role), "TEST-AUTH", "").expectStatus().isForbidden();
  }

  @Test
  void readingMovementsNeedsAToken() {
    http.get()
        .uri("/stock/{variantId}/movements", "TEST-AUTH")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  private RestTestClient.ResponseSpec movements(String token, String variantId, String query) {
    return http.get()
        .uri("/stock/" + variantId + "/movements" + query)
        .headers(h -> h.setBearerAuth(token))
        .exchange();
  }

  /** A movement as the ledger shows it, ignoring when it happened. */
  private static MovementView movement(
      String kind, int onHandChange, int reservedChange, String reservationId, String reason) {
    return new MovementView(kind, onHandChange, reservedChange, reservationId, reason, null);
  }
}
