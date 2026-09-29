package com.ecomm.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Available Stock is on-hand minus the Reservations holding it: only {@code ACTIVE} ones, and only
 * until their {@code expiresAt}, whether or not the sweeper has released them yet.
 */
class StockTest {

  private static final String VARIANT = "PHN-PIXEL-9";
  private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

  @Test
  void anActiveReservationReducesAvailableStockButNotOnHand() {
    var stock = Stock.of(VARIANT, 10, List.of(active(3, NOW.plusSeconds(60))), NOW);

    assertThat(stock.onHand()).isEqualTo(10);
    assertThat(stock.reserved()).isEqualTo(3);
    assertThat(stock.available()).isEqualTo(7);
  }

  @Test
  void anActiveReservationPastItsExpiryHoldsNothing() {
    var expiresAt = NOW.plusSeconds(60);
    var reservations = List.of(active(3, expiresAt), active(2, NOW.plusSeconds(600)));

    assertThat(Stock.of(VARIANT, 10, reservations, expiresAt.minusNanos(1_000)).available())
        .isEqualTo(5);
    assertThat(Stock.of(VARIANT, 10, reservations, expiresAt).available()).isEqualTo(8);
    assertThat(Stock.of(VARIANT, 10, reservations, expiresAt.plus(Duration.ofDays(1))).available())
        .isEqualTo(10);
  }

  @Test
  void committedAndReleasedReservationsHoldNothing() {
    var expiresAt = NOW.plusSeconds(60);
    var committed = active(3, expiresAt).commitAt(NOW);
    var released = active(4, expiresAt).release();

    var stock = Stock.of(VARIANT, 10, List.of(committed, released), NOW);

    assertThat(stock.reserved()).isZero();
    assertThat(stock.available()).isEqualTo(10);
  }

  @Test
  void onlyTheVariantsOwnQuantityCounts() {
    var reservation =
        reservation(
            StockBatch.of(
                List.of(new StockBatch.Line(VARIANT, 2), new StockBatch.Line("AUD-JBL-FLIP-6", 5))),
            NOW.plusSeconds(60));

    assertThat(Stock.of(VARIANT, 10, List.of(reservation), NOW).available()).isEqualTo(8);
  }

  @Test
  void onHandCannotBeSetBelowTheReservedQuantity() {
    var stock = Stock.of(VARIANT, 10, List.of(active(3, NOW.plusSeconds(60))), NOW);

    assertThat(stock.withOnHand(3).available()).isZero();
    assertThatThrownBy(() -> stock.withOnHand(2)).isInstanceOf(OnHandBelowReservedException.class);
  }

  private static Reservation active(int quantity, Instant expiresAt) {
    return reservation(StockBatch.of(List.of(new StockBatch.Line(VARIANT, quantity))), expiresAt);
  }

  private static Reservation reservation(StockBatch items, Instant expiresAt) {
    return Reservation.create(UUID.randomUUID(), "customer-42", items, expiresAt, NOW);
  }
}
