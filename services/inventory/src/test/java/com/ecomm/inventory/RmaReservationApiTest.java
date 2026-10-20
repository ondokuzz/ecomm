package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The returns Saga holds a replacement unit with a Reservation owned by the RMA, {@code
 * rma:<rmaId>}, with Orchestration's own token, and commits it as the checkout Saga commits a
 * Customer's. An RMA has one Reservation: asking again answers the first, so a retried step
 * reserves once.
 */
class RmaReservationApiTest extends InventoryApiTest {

  @Test
  void orchestrationReservesForAnRma() {
    var variant = newVariant(5);
    var owner = rmaOwner();

    var reservation =
        reserve(orchestrationToken(), reservationBody(owner, inFifteenMinutes(), item(variant, 1)))
            .expectStatus()
            .isCreated()
            .expectBody(ReservationView.class)
            .returnResult()
            .getResponseBody();

    assertThat(reservation.customerId()).isEqualTo(owner);
    assertThat(reservation.status()).isEqualTo("ACTIVE");
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 4, 5, 1));
    assertThat(ledgerOf(variant).changes().getFirst())
        .isEqualTo(new MovementView("RESERVED", 0, 1, reservation.id(), null, owner, null));
  }

  @Test
  void aSecondRequestForTheSameRmaAnswersTheFirst() {
    var variant = newVariant(1);
    var owner = rmaOwner();
    var body = reservationBody(owner, inFifteenMinutes(), item(variant, 1));
    var first =
        reserve(orchestrationToken(), body)
            .expectStatus()
            .isCreated()
            .expectBody(ReservationView.class)
            .returnResult()
            .getResponseBody();
    // Read once, so the Stock cache shows the last unit held.
    assertThat(stockOf(variant).quantity()).isZero();

    reserve(orchestrationToken(), body)
        .expectStatus()
        .isOk()
        .expectBody(ReservationView.class)
        .isEqualTo(first);

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 0, 1, 1));
    assertThat(ledgerOf(variant).changes()).filteredOn(m -> m.kind().equals("RESERVED")).hasSize(1);
  }

  @Test
  void aSecondRequestAnswersTheFirstOnceCommitted() {
    var variant = newVariant(2);
    var owner = rmaOwner();
    var body = reservationBody(owner, inFifteenMinutes(), item(variant, 1));
    var first = reservedFor(owner, variant);
    settle(orchestrationToken(), first.id(), "commit", owner).expectStatus().isOk();

    reserve(orchestrationToken(), body)
        .expectStatus()
        .isOk()
        .expectBody(ReservationView.class)
        .value(r -> assertThat(r.id()).isEqualTo(first.id()))
        .value(r -> assertThat(r.status()).isEqualTo("COMMITTED"));

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 1, 1, 0));
  }

  @Test
  void anRmasReservationThatExpiredUncommittedIsReplacedByANewOne() {
    var variant = newVariant(1);
    var owner = rmaOwner();
    var expired = reservedFor(owner, variant);
    clock.advanceBy(Duration.ofMinutes(16));

    var replaced =
        reserve(orchestrationToken(), reservationBody(owner, inFifteenMinutes(), item(variant, 1)))
            .expectStatus()
            .isCreated()
            .expectBody(ReservationView.class)
            .returnResult()
            .getResponseBody();

    assertThat(replaced.id()).isNotEqualTo(expired.id());
    settle(orchestrationToken(), replaced.id(), "commit", owner).expectStatus().isOk();
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 0, 0, 0));
    var ledger = ledgerOf(variant);
    assertThat(ledger.changes())
        .filteredOn(m -> expired.id().equals(m.reservationId()))
        .extracting(MovementView::kind)
        .containsExactly("RELEASED", "RESERVED");
    assertThat(ledger.balanced()).isTrue();
  }

  @Test
  void checkoutCantReleaseAnRmasReservation() {
    var variant = newVariant(3);
    var owner = rmaOwner();
    var reservation = reservedFor(owner, variant);

    settle(checkoutToken(), reservation.id(), "release", owner).expectStatus().isForbidden();

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 2, 3, 1));
  }

  @Test
  void tooLittleStockIsInsufficientStock() {
    var variant = newVariant(0);

    reserve(orchestrationToken(), reservationBody(rmaOwner(), inFifteenMinutes(), item(variant, 1)))
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.insufficientStock[0]")
        .isEqualTo(variant);

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 0, 0, 0));
  }

  @Test
  void orchestrationCommitsAnRmasReservationWithItsReference() {
    var variant = newVariant(3);
    var owner = rmaOwner();
    var reservation = reservedFor(owner, variant);

    settle(orchestrationToken(), reservation.id(), "commit", owner)
        .expectStatus()
        .isOk()
        .expectBody(ReservationView.class)
        .value(r -> assertThat(r.status()).isEqualTo("COMMITTED"));

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 2, 2, 0));
    var ledger = ledgerOf(variant);
    assertThat(ledger.changes().getFirst())
        .isEqualTo(new MovementView("COMMITTED", -1, -1, reservation.id(), null, owner, null));
    assertThat(ledger.balanced()).isTrue();
    assertThat(ledger.onHandFromMovements()).isEqualTo(2);
  }

  @Test
  void aCustomersReservationNamesNoReference() {
    var variant = newVariant(3);
    var reservation = reserved(item(variant, 1));

    commit(reservation.id()).expectStatus().isOk();

    assertThat(ledgerOf(variant).changes())
        .filteredOn(m -> reservation.id().equals(m.reservationId()))
        .extracting(MovementView::reference)
        .containsOnlyNulls();
  }

  @Test
  void orchestrationReservesOnlyForAnRmaAndCheckoutOnlyForACustomer() {
    var variant = newVariant(3);

    reserve(orchestrationToken(), reservationBody(CUSTOMER, inFifteenMinutes(), item(variant, 1)))
        .expectStatus()
        .isForbidden();
    reserve(orchestrationToken(), reservationBody("rma:", inFifteenMinutes(), item(variant, 1)))
        .expectStatus()
        .isForbidden();
    reserve(checkoutToken(), reservationBody(rmaOwner(), inFifteenMinutes(), item(variant, 1)))
        .expectStatus()
        .isForbidden();

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 3, 3, 0));
  }

  @Test
  void anRmasReservationIsAnotherOwnersNotFound() {
    var variant = newVariant(3);
    var reservation = reservedFor(rmaOwner(), variant);

    settle(orchestrationToken(), reservation.id(), "commit", rmaOwner())
        .expectStatus()
        .isNotFound();

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 2, 3, 1));
  }

  private ReservationView reservedFor(String owner, String variant) {
    return reserve(
            orchestrationToken(), reservationBody(owner, inFifteenMinutes(), item(variant, 1)))
        .expectStatus()
        .isCreated()
        .expectBody(ReservationView.class)
        .returnResult()
        .getResponseBody();
  }

  private static String rmaOwner() {
    return "rma:" + UUID.randomUUID();
  }
}
