package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.inventory.application.port.in.PublishBackfillUseCase;
import com.ecomm.inventory.application.port.in.ReleaseExpiredReservationsUseCase;
import java.time.Duration;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Stock and Reservations from before the ledger (a Sprint 2 stack's, from {@code db/testdata}) get
 * opening balances from the migration, and each Variant's Stock is published once with change
 * {@code BACKFILLED} when the service starts; starting again publishes nothing more.
 */
class BackfillApiTest extends InventoryApiTest {

  private static final String HELD = "SPRINT2-HELD";
  private static final String MOVING = "SPRINT2-MOVING";
  private static final String EXPIRED = "SPRINT2-EXPIRED";
  private static final String ACTIVE_RESERVATION = "52000000-0000-4000-8000-000000000001";
  private static final String EXPIRED_RESERVATION = "52000000-0000-4000-8000-000000000002";

  @Autowired PublishBackfillUseCase backfill;
  @Autowired ReleaseExpiredReservationsUseCase sweeper;

  @Test
  void onHandOpensAsAnAdjustmentAndActiveReservationsAsReserved() {
    var ledger = ledgerOf(HELD);

    assertThat(ledger.changes())
        .containsExactly(
            new MovementView("RESERVED", 0, 2, ACTIVE_RESERVATION, "opening balance", null),
            new MovementView("ADJUSTED", 9, 0, null, "opening balance", null));
    assertThat(ledger.balanced()).isTrue();
  }

  @Test
  void everySeededVariantOpensBalanced() {
    for (var variant : new String[] {"PHN-PIXEL-9", "LPT-FRAMEWORK-13", "TEST-AUTH"}) {
      assertThat(ledgerOf(variant).balanced()).as(variant).isTrue();
    }
  }

  @Test
  void anExpiredReservationOpensReservedAndTheSweepReleasesIt() {
    sweeper.releaseExpired();

    var ledger = ledgerOf(EXPIRED);

    assertThat(ledger.changes())
        .containsExactly(
            new MovementView("RELEASED", 0, -3, EXPIRED_RESERVATION, null, null),
            new MovementView("RESERVED", 0, 3, EXPIRED_RESERVATION, "opening balance", null),
            new MovementView("ADJUSTED", 5, 0, null, "opening balance", null));
    assertThat(ledger.balanced()).isTrue();
  }

  @Test
  void everyExistingVariantsStockIsPublishedOnceAsBackfilled() {
    var held = StockEvents.keyed(HELD, 1);
    var seeded = StockEvents.keyed("LPT-FRAMEWORK-13", 1);

    assertThat(held).hasSize(1);
    assertThat(seeded).hasSize(1);
    assertThat(StockEvents.schemaViolationsOf(held.getFirst())).isEmpty();
    var event = StockEvents.valueOf(held.getFirst());
    assertThat(event.get("change").asText()).isEqualTo("BACKFILLED");
    assertThat(event.at("/stock/onHand").asInt()).isEqualTo(9);
    assertThat(event.at("/stock/available").asInt()).isEqualTo(7);
    assertThat(event.at("/stock/stocked").asBoolean()).isTrue();
    assertThat(StockEvents.valueOf(seeded.getFirst()).at("/stock/onHand").asInt()).isEqualTo(5);
  }

  @Test
  void runningTheBackfillAgainPublishesNothingMore() {
    StockEvents.keyed(HELD, 1);

    assertThat(backfill.publishBackfill()).isZero();

    assertThat(StockEvents.keyed(HELD, 2, Duration.ofSeconds(3))).hasSize(1);
  }

  @Test
  void aBackfilledVariantMovesOnFromItsBackfilledVersion() {
    StockEvents.keyed(MOVING, 1);

    reserved(item(MOVING, 2));

    var events = StockEvents.keyed(MOVING, 2).stream().map(StockEvents::valueOf).toList();
    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("BACKFILLED", "RESERVED");
    assertThat(events)
        .extracting(e -> e.get("version").asLong())
        .isSortedAccordingTo(Comparator.naturalOrder())
        .doesNotHaveDuplicates();
    assertThat(events.getLast().at("/stock/available").asInt()).isEqualTo(1);
    assertThat(ledgerOf(MOVING).balanced()).isTrue();
  }
}
