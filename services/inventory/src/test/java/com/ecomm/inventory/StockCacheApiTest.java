package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.ecomm.inventory.application.port.in.ReleaseExpiredReservationsUseCase;
import com.ecomm.inventory.application.port.out.CachedStock;
import com.ecomm.inventory.application.port.out.StockCache;
import com.ecomm.inventory.domain.Stock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Stock reads are served from the Stock cache in Redis, which each change writes once it commits. A
 * cache hit or miss shows on {@code /actuator/prometheus}; each test starts with an empty cache.
 */
class StockCacheApiTest extends InventoryApiTest {

  static final String HITS = "ecomm_stock_cache_hits_total";
  static final String MISSES = "ecomm_stock_cache_misses_total";

  @Autowired ReleaseExpiredReservationsUseCase sweeper;
  @Autowired StockCache cache;
  @Autowired DataSource dataSource;

  @BeforeEach
  void emptyTheCache() throws Exception {
    REDIS.execInContainer("redis-cli", "FLUSHALL");
  }

  @Test
  void aReadAfterAReadIsAHit() {
    var hits = counter(HITS);
    var misses = counter(MISSES);

    var first = stockOf("PHN-PIXEL-9");
    assertThat(counter(MISSES)).isEqualTo(misses + 1);
    assertThat(counter(HITS)).isEqualTo(hits);

    assertThat(stockOf("PHN-PIXEL-9")).isEqualTo(first);
    assertThat(counter(HITS)).isEqualTo(hits + 1);
    assertThat(counter(MISSES)).isEqualTo(misses + 1);
  }

  @Test
  void addingAVariantWritesItsEntry() {
    var variant = newVariant(10);

    assertHit(variant, new StockView(variant, 10, 10, 0));
  }

  @Test
  void settingOnHandWritesTheEntry() {
    var variant = newVariant(10);
    stockOf(variant);

    setOnHand(staffToken(), variant, onHandBody(7)).expectStatus().isOk();

    assertHit(variant, new StockView(variant, 7, 7, 0));
  }

  @Test
  void reservingReleasingAndCommittingWriteTheEntry() {
    var variant = newVariant(10);
    stockOf(variant);

    var released = reserved(item(variant, 3));
    assertHit(variant, new StockView(variant, 7, 10, 3));
    release(released.id()).expectStatus().isOk();
    assertHit(variant, new StockView(variant, 10, 10, 0));

    var committed = reserved(item(variant, 4));
    assertHit(variant, new StockView(variant, 6, 10, 4));
    commit(committed.id()).expectStatus().isOk();
    assertHit(variant, new StockView(variant, 6, 6, 0));
  }

  @Test
  void theSweepWritesTheEntry() {
    var variant = newVariant(10);
    reserved(clock.now().plusSeconds(30), item(variant, 3));
    clock.advanceBy(Duration.ofSeconds(30));

    sweeper.releaseExpired();

    assertHit(variant, new StockView(variant, 10, 10, 0));
  }

  @Test
  void removingAVariantWritesItsEntry() {
    var variant = newVariant(10);
    stockOf(variant);

    removeStock(staffToken(), variant).expectStatus().isNoContent();

    var hits = counter(HITS);
    http.get().uri("/stock/{variantId}", variant).exchange().expectStatus().isNotFound();
    assertThat(counter(HITS)).isEqualTo(hits + 1);
  }

  @Test
  void aWriteOlderThanTheEntryIsIgnored() {
    var variant = newVariant(10);
    setOnHand(staffToken(), variant, onHandBody(7)).expectStatus().isOk();

    // A write that lands late: the Variant's Stock before any change Inventory made to it.
    cache.put(List.of(CachedStock.of(new Stock(variant, 99, 0), 0, later())), clock.now());

    assertHit(variant, new StockView(variant, 7, 7, 0));
  }

  @Test
  void stockAReservationGivesBackIsAvailableTheMomentItExpires() {
    var variant = newVariant(10);
    var expiresAt = clock.now().plusSeconds(30);
    reserved(expiresAt, item(variant, 4));
    assertHit(variant, new StockView(variant, 6, 10, 4));

    clock.advanceTo(expiresAt);

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 10, 10, 0));
  }

  @Test
  void anEntryIsTrustedForSixtySecondsAtMost() {
    var variant = newVariant(10);
    reserved(clock.now().plus(Duration.ofMinutes(15)), item(variant, 4));
    clock.advanceBy(Duration.ofSeconds(60));

    var misses = counter(MISSES);
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 6, 10, 4));
    assertThat(counter(MISSES)).isEqualTo(misses + 1);
  }

  @Test
  void aReservationTheCacheShowsCantBeMetIsRefusedWithoutWaitingForPostgres() throws Exception {
    var variant = newVariant(10);
    // Any Reservation that went to Postgres would wait for this lock on the Variant's Stock.
    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var lock =
          connection.prepareStatement("SELECT 1 FROM stock WHERE variant_id = ? FOR UPDATE")) {
        lock.setString(1, variant);
        lock.executeQuery();
      }
      try {
        assertTimeoutPreemptively(
            Duration.ofSeconds(5),
            () ->
                reserve(reservationBody(CUSTOMER, inFifteenMinutes(), item(variant, 11)))
                    .expectStatus()
                    .isEqualTo(409)
                    .expectHeader()
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody()
                    .jsonPath("$.insufficientStock[0]")
                    .isEqualTo(variant));
      } finally {
        connection.rollback();
      }
    }
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 10, 10, 0));
  }

  @Test
  void aReservationTheCacheShowsCanBeMetIsDecidedByPostgres() {
    var variant = newVariant(10);
    stockOf(variant);

    reserved(item(variant, 10));

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 0, 10, 10));
  }

  /** The Variant's Stock is {@code expected}, read from the cache. */
  private void assertHit(String variant, StockView expected) {
    var hits = counter(HITS);
    var misses = counter(MISSES);
    assertThat(stockOf(variant)).isEqualTo(expected);
    assertThat(counter(HITS)).as("hits").isEqualTo(hits + 1);
    assertThat(counter(MISSES)).as("misses").isEqualTo(misses);
  }

  private Instant later() {
    return clock.now().plusSeconds(30);
  }
}
