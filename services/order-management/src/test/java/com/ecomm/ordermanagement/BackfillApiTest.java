package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.ordermanagement.application.port.in.PublishBackfillUseCase;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Orders placed before Order Status histories and Order events (a Sprint 2 stack's, from {@code
 * db/testdata}) get a history from the migration and are published once with change {@code
 * BACKFILLED} when the service starts; starting again publishes nothing more.
 */
class BackfillApiTest extends OrderApiTest {

  private static final String CUSTOMER = "customer-sprint2";
  private static final String PLACED_ORDER = "5e000000-0000-4000-8000-000000000001";
  private static final String CANCELLED_ORDER = "5e000000-0000-4000-8000-000000000002";
  private static final String MOVING_ORDER = "5e000000-0000-4000-8000-000000000003";

  @Autowired PublishBackfillUseCase backfill;

  @Test
  void aPlacedOrderGetsItsPlacementAsItsHistory() {
    assertThat(read(PLACED_ORDER).statusHistory())
        .containsExactly(new HistoryEntryView("PLACED", "2026-09-20T09:00:00Z", "CHECKOUT", false));
  }

  @Test
  void anOrderThatMovedOnGetsItsCurrentStatusBackfilledAtItsPlacement() {
    assertThat(read(CANCELLED_ORDER).statusHistory())
        .containsExactly(
            new HistoryEntryView("PLACED", "2026-09-21T09:00:00Z", "CHECKOUT", false),
            new HistoryEntryView("CANCELLED", "2026-09-21T09:00:00Z", "CHECKOUT", true));
  }

  @Test
  void everyExistingOrderIsPublishedOnceAsBackfilled() {
    var placed = OrderEvents.keyed(PLACED_ORDER, 1);
    var cancelled = OrderEvents.keyed(CANCELLED_ORDER, 1);

    assertThat(placed).hasSize(1);
    assertThat(cancelled).hasSize(1);
    assertThat(OrderEvents.schemaViolationsOf(placed.getFirst())).isEmpty();
    assertThat(OrderEvents.schemaViolationsOf(cancelled.getFirst())).isEmpty();
    var placedEvent = OrderEvents.valueOf(placed.getFirst());
    assertThat(placedEvent.get("change").asText()).isEqualTo("BACKFILLED");
    assertThat(placedEvent.get("version").asLong()).isEqualTo(1);
    var cancelledEvent = OrderEvents.valueOf(cancelled.getFirst());
    assertThat(cancelledEvent.get("change").asText()).isEqualTo("BACKFILLED");
    assertThat(cancelledEvent.get("version").asLong()).isEqualTo(2);
    assertThat(cancelledEvent.at("/order/status").asText()).isEqualTo("CANCELLED");
    assertThat(cancelledEvent.at("/order/discounts/0/couponCode").asText()).isEqualTo("WELCOME10");
    assertThat(cancelledEvent.at("/order/statusHistory/1/backfilled").asBoolean()).isTrue();
  }

  @Test
  void runningTheBackfillAgainPublishesNothingMore() {
    OrderEvents.keyed(PLACED_ORDER, 1);

    assertThat(backfill.publishBackfill()).isZero();

    assertThat(OrderEvents.keyed(PLACED_ORDER, 2, Duration.ofSeconds(3))).hasSize(1);
    assertThat(OrderEvents.keyed(CANCELLED_ORDER, 2, Duration.ofSeconds(3))).hasSize(1);
  }

  @Test
  void aBackfilledOrderMovesOnFromItsBackfilledVersion() {
    OrderEvents.keyed(MOVING_ORDER, 1);

    changeStatus(checkoutToken(), MOVING_ORDER, statusChange(CUSTOMER, "PAID"))
        .expectStatus()
        .isOk();

    var events = OrderEvents.keyed(MOVING_ORDER, 2).stream().map(OrderEvents::valueOf).toList();
    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("BACKFILLED", "STATUS_CHANGED");
    assertThat(events).extracting(e -> e.get("version").asLong()).containsExactly(1L, 2L);
    assertThat(read(MOVING_ORDER).statusHistory())
        .extracting(HistoryEntryView::status)
        .containsExactly("PLACED", "PAID");
  }

  private OrderView read(String id) {
    return http.get()
        .uri("/orders/{id}", id)
        .headers(h -> h.setBearerAuth(tokenOf(CUSTOMER)))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(OrderView.class)
        .returnResult()
        .getResponseBody();
  }
}
