package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.inventory.application.port.in.ReleaseExpiredReservationsUseCase;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * A Reservation stops holding Stock the moment it expires, before any sweep. The sweep only tidies
 * up by marking expired Reservations released; the scheduled one is off here, so each test sweeps
 * itself.
 */
class ReservationExpiryApiTest extends InventoryApiTest {

  @Autowired ReleaseExpiredReservationsUseCase sweeper;

  @Test
  void availableStockRecoversAtTheMomentOfExpiryBeforeAnySweep() {
    var variant = newVariant(10);
    var expiresAt = clock.now().plus(Duration.ofMinutes(15));
    reserved(expiresAt, item(variant, 4));

    clock.advanceTo(expiresAt.minusNanos(1_000));
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 6, 10, 4));

    clock.advanceTo(expiresAt);
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 10, 10, 0));
  }

  @Test
  void anExpiredReservationsStockCanBeReservedAgainBeforeAnySweep() {
    var variant = newVariant(1);
    reserved(clock.now().plusSeconds(60), item(variant, 1));

    clock.advanceBy(Duration.ofSeconds(60));

    reserve(reservationBody("someone-else", inFifteenMinutes(), item(variant, 1)))
        .expectStatus()
        .isCreated();
  }

  @Test
  void committingAnExpiredReservationIsAConflictWhetherOrNotItWasSwept() {
    var variant = newVariant(10);
    var reservation = reserved(clock.now().plusSeconds(60), item(variant, 3));
    clock.advanceBy(Duration.ofSeconds(60));

    expectExpired(reservation.id());
    sweeper.releaseExpired();
    expectExpired(reservation.id());

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 10, 10, 0));
  }

  @Test
  void releasingAnExpiredReservationSucceeds() {
    var reservation = reserved(clock.now().plusSeconds(60), item(newVariant(10), 3));
    clock.advanceBy(Duration.ofSeconds(60));

    release(reservation.id())
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("RELEASED");
  }

  @Test
  void theSweepReleasesExpiredReservationsOnly() {
    var variant = newVariant(10);
    var expiring = reserved(clock.now().plusSeconds(60), item(variant, 1));
    var lasting = reserved(clock.now().plus(Duration.ofHours(1)), item(variant, 2));
    var committed = reserved(clock.now().plusSeconds(60), item(variant, 3));
    commit(committed.id()).expectStatus().isOk();
    clock.advanceBy(Duration.ofSeconds(60));

    var released = sweeper.releaseExpired();

    assertThat(released)
        .contains(UUID.fromString(expiring.id()))
        .doesNotContain(UUID.fromString(lasting.id()), UUID.fromString(committed.id()));
    assertThat(sweeper.releaseExpired()).doesNotContain(UUID.fromString(expiring.id()));
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 5, 7, 2));
    commit(lasting.id()).expectStatus().isOk();
  }

  private void expectExpired(String id) {
    commit(id)
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reservationExpired")
        .isEqualTo(id);
  }
}
