package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;

/**
 * The checkout Saga commits a Customer's Reservation with Orchestration's own token, naming the
 * Customer in the body. Committing is naturally idempotent, so it takes no key. Holding and
 * releasing Stock stay Checkout's, and committing is Orchestration's alone.
 */
class OrchestrationReservationApiTest extends InventoryApiTest {

  private static final String ORCHESTRATION = FakeKeycloak.token("orchestration", "ORCHESTRATION");

  @Test
  void orchestrationCommitsAReservationAndCommittingAgainChangesNothing() {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 2));

    var committed = settle(ORCHESTRATION, reservation.id(), "commit", CUSTOMER);
    var again = settle(ORCHESTRATION, reservation.id(), "commit", CUSTOMER);

    committed
        .expectStatus()
        .isOk()
        .expectBody(ReservationView.class)
        .value(r -> assertThat(r.status()).isEqualTo("COMMITTED"));
    again
        .expectStatus()
        .isOk()
        .expectBody(ReservationView.class)
        .value(r -> assertThat(r.status()).isEqualTo("COMMITTED"));
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 8, 8, 0));
  }

  @Test
  void orchestrationCanNeitherReserveNorRelease() {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 1));

    reserve(ORCHESTRATION, reservationBody(CUSTOMER, inFifteenMinutes(), item(variant, 1)))
        .expectStatus()
        .isForbidden();
    settle(ORCHESTRATION, reservation.id(), "release", CUSTOMER).expectStatus().isForbidden();

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 9, 10, 1));
  }

  @Test
  void checkoutCanNoLongerCommit() {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 1));

    settle(checkoutToken(), reservation.id(), "commit", CUSTOMER).expectStatus().isForbidden();

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 9, 10, 1));
  }
}
