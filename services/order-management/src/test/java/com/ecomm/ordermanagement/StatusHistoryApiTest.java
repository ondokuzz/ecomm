package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Every Order Status an Order has been in, oldest first, with when and who moved it: placement and
 * each change append to its Order Status history, and a refused change leaves it as it was.
 */
class StatusHistoryApiTest extends OrderApiTest {

  @Test
  void aPlacedOrderShowsItsPlacementByCheckoutAtItsPlacedAt() {
    var order = placed();

    assertThat(order.statusHistory())
        .containsExactly(new HistoryEntryView("PLACED", order.placedAt(), "CHECKOUT", false));
  }

  @Test
  void aStatusChangeAppendsToTheHistoryAndTheResponseShowsIt() {
    var order = placed();

    var paid =
        changeStatus(order.id(), "PAID")
            .expectStatus()
            .isOk()
            .expectBody(OrderView.class)
            .returnResult()
            .getResponseBody();

    assertThat(paid.statusHistory()).hasSize(2);
    assertThat(paid.statusHistory().getFirst()).isEqualTo(order.statusHistory().getFirst());
    var payment = paid.statusHistory().getLast();
    assertThat(payment.status()).isEqualTo("PAID");
    assertThat(payment.changedBy()).isEqualTo("CHECKOUT");
    assertThat(payment.backfilled()).isFalse();
    assertThat(Instant.parse(payment.at())).isAfterOrEqualTo(Instant.parse(order.placedAt()));
    assertThat(read(order.id())).isEqualTo(paid);
  }

  @Test
  void aPaidThenCancelledOrderShowsPlacedThenPaidThenCancelled() {
    var id = placed().id();
    changeStatus(id, "PAID").expectStatus().isOk();
    changeStatus(id, "CANCELLED").expectStatus().isOk();

    var history = read(id).statusHistory();

    assertThat(history)
        .extracting(HistoryEntryView::status)
        .containsExactly("PLACED", "PAID", "CANCELLED");
    assertThat(history)
        .extracting(entry -> Instant.parse(entry.at()))
        .isSortedAccordingTo(Instant::compareTo);
  }

  @Test
  void aRefusedChangeLeavesTheHistoryAsItWas() {
    var order = placed();

    changeStatus(order.id(), "SHIPPED").expectStatus().isEqualTo(409);

    assertThat(read(order.id()).statusHistory()).isEqualTo(order.statusHistory());
  }

  private OrderView read(String id) {
    return http.get()
        .uri("/orders/{id}", id)
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(OrderView.class)
        .returnResult()
        .getResponseBody();
  }
}
