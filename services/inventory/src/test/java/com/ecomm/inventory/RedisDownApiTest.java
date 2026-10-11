package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Stock cache is never required: with its Redis stopped, Stock reads, Reservations and Staff's
 * changes all go to Postgres, each failed call to Redis counted, and Inventory stays healthy.
 */
class RedisDownApiTest extends InventoryApiTest {

  static final String ERRORS = "ecomm_stock_cache_errors_total";

  @BeforeEach
  void stopRedis() {
    REDIS.getDockerClient().stopContainerCmd(REDIS.getContainerId()).exec();
  }

  @AfterEach
  void startRedisAgain() {
    REDIS.getDockerClient().startContainerCmd(REDIS.getContainerId()).exec();
    // Back once a read is served without an error, so later tests find the cache working.
    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              var errors = counter(ERRORS);
              stockOf("PHN-PIXEL-9");
              assertThat(counter(ERRORS)).isEqualTo(errors);
            });
  }

  @Test
  void stockIsReadAndReservedFromPostgres() {
    var errors = counter(ERRORS);

    var variant = newVariant(10);
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 10, 10, 0));
    var reservation = reserved(item(variant, 4));
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 6, 10, 4));
    commit(reservation.id()).expectStatus().isOk();
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 6, 6, 0));
    reserve(reservationBody(CUSTOMER, inFifteenMinutes(), item(variant, 7)))
        .expectStatus()
        .isEqualTo(409);

    assertThat(counter(ERRORS)).isGreaterThan(errors);
    http.get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }
}
