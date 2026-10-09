package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * Checkout holds Stock for a Customer with a Reservation, then commits it to take the Stock for
 * good or releases it to give the Stock back.
 */
class ReservationApiTest extends InventoryApiTest {

  @Test
  void reservingReducesAvailableStockButNotOnHand() {
    var variant = newVariant(10);
    var expiresAt = inFifteenMinutes();

    reserve(reservationBody(CUSTOMER, expiresAt, item(variant, 3)))
        .expectStatus()
        .isCreated()
        .expectHeader()
        .valueMatches("Location", "/reservations/[0-9a-f-]{36}")
        .expectBody()
        .jsonPath("$.id")
        .isNotEmpty()
        .jsonPath("$.customerId")
        .isEqualTo(CUSTOMER)
        .jsonPath("$.status")
        .isEqualTo("ACTIVE")
        .jsonPath("$.expiresAt")
        .isEqualTo(expiresAt.toString())
        .jsonPath("$.items[0].variantId")
        .isEqualTo(variant)
        .jsonPath("$.items[0].quantity")
        .isEqualTo(3);

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 7, 10, 3));
  }

  @Test
  void aReservationIsAllOrNothing() {
    var plenty = newVariant(10);
    var scarce = newVariant(1);

    reserve(reservationBody(CUSTOMER, inFifteenMinutes(), item(plenty, 2), item(scarce, 2)))
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.insufficientStock.length()")
        .isEqualTo(1)
        .jsonPath("$.insufficientStock[0]")
        .isEqualTo(scarce);

    reserve(reservationBody(CUSTOMER, inFifteenMinutes(), item(plenty, 2), item("NO-SUCH", 1)))
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.unknownVariants[0]")
        .isEqualTo("NO-SUCH");

    assertThat(stockOf(plenty)).isEqualTo(new StockView(plenty, 10, 10, 0));
    assertThat(stockOf(scarce)).isEqualTo(new StockView(scarce, 1, 1, 0));
  }

  @Test
  void theLastUnitCanBeReservedOnlyOnce() {
    var variant = newVariant(1);

    reserved(item(variant, 1));

    reserve(reservationBody("someone-else", inFifteenMinutes(), item(variant, 1)))
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.insufficientStock[0]")
        .isEqualTo(variant);
  }

  @Test
  void concurrentReservationsOfTheLastUnitLetOnlyOneThrough() throws Exception {
    var variant = newVariant(1);
    var other = newVariant(10);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(8)) {
      var attempts = new ArrayList<Future<Integer>>();
      for (int i = 0; i < 8; i++) {
        // Half list the Variants the other way round; locking in ID order keeps them deadlock-free.
        var items =
            i % 2 == 0
                ? new Item[] {item(variant, 1), item(other, 1)}
                : new Item[] {item(other, 1), item(variant, 1)};
        var body = reservationBody("customer-" + i, inFifteenMinutes(), items);
        attempts.add(
            pool.submit(
                () -> {
                  start.await();
                  return reserve(body).returnResult().getStatus().value();
                }));
      }
      start.countDown();
      var statuses = new ArrayList<Integer>();
      for (var attempt : attempts) {
        statuses.add(attempt.get(30, TimeUnit.SECONDS));
      }

      assertThat(statuses).containsOnly(201, 409).containsOnlyOnce(201);
    }
    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 0, 1, 1));
    assertThat(stockOf(other)).isEqualTo(new StockView(other, 9, 10, 1));
  }

  @Test
  void insufficientStockCountsOnlyActiveReservations() {
    var variant = newVariant(5);
    var committed = reserved(item(variant, 1));
    commit(committed.id()).expectStatus().isOk();
    var released = reserved(item(variant, 2));
    release(released.id()).expectStatus().isOk();
    reserved(item(variant, 1));

    // On hand: 4 after the commit. Held: 1 by the active Reservation. The released 2 count for
    // nothing.
    reserve(reservationBody(CUSTOMER, inFifteenMinutes(), item(variant, 4)))
        .expectStatus()
        .isEqualTo(409);
    reserve(reservationBody(CUSTOMER, inFifteenMinutes(), item(variant, 3)))
        .expectStatus()
        .isCreated();

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 0, 4, 4));
  }

  @Test
  void aVariantListedTwiceIsReservedForTheTotal() {
    var variant = newVariant(5);

    var reservation = reserved(item(variant, 2), item(variant, 3));

    assertThat(reservation.items()).containsExactly(new ItemView(variant, 5));
    assertThat(quantityOf(variant)).isZero();
  }

  @Test
  void committingTakesTheStockOffOnHand() {
    var a = newVariant(10);
    var b = newVariant(4);
    var reservation = reserved(item(a, 3), item(b, 4));

    commit(reservation.id())
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("COMMITTED");

    assertThat(stockOf(a)).isEqualTo(new StockView(a, 7, 7, 0));
    assertThat(stockOf(b)).isEqualTo(new StockView(b, 0, 0, 0));
  }

  @Test
  void committingTwiceTakesTheStockOnce() {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 3));

    commit(reservation.id()).expectStatus().isOk();
    commit(reservation.id())
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("COMMITTED");

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 7, 7, 0));
  }

  @Test
  void releasingGivesTheStockBackAndIsIdempotent() {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 3));

    for (int i = 0; i < 2; i++) {
      release(reservation.id())
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.status")
          .isEqualTo("RELEASED");
      assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 10, 10, 0));
    }
  }

  @Test
  void committingAReleasedReservationIsAConflict() {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 3));
    release(reservation.id()).expectStatus().isOk();

    commit(reservation.id())
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reservationReleased")
        .isEqualTo(reservation.id());

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 10, 10, 0));
  }

  @Test
  void releasingACommittedReservationIsAConflict() {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 3));
    commit(reservation.id()).expectStatus().isOk();

    release(reservation.id())
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reservationCommitted")
        .isEqualTo(reservation.id());

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 7, 7, 0));
  }

  @Test
  void anotherCustomersReservationIsNotFound() {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 3));

    for (var action : new String[] {"commit", "release"}) {
      settle(tokenToSettle(action), reservation.id(), action, "someone-else")
          .expectStatus()
          .isNotFound()
          .expectHeader()
          .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 7, 10, 3));
  }

  @ParameterizedTest
  @ValueSource(strings = {"00000000-0000-0000-0000-000000000000", "not-a-uuid"})
  void anUnknownReservationIsNotFound(String id) {
    commit(id).expectStatus().isNotFound();
    release(id).expectStatus().isNotFound();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {"expiresAt": "2999-01-01T00:00:00Z", "items": [{"variantId": "PHN-PIXEL-9", "quantity": 1}]}
        """,
        """
        {"customerId": " ", "expiresAt": "2999-01-01T00:00:00Z",
         "items": [{"variantId": "PHN-PIXEL-9", "quantity": 1}]}
        """,
        """
        {"customerId": "customer-42", "items": [{"variantId": "PHN-PIXEL-9", "quantity": 1}]}
        """,
        """
        {"customerId": "customer-42", "expiresAt": "tomorrow",
         "items": [{"variantId": "PHN-PIXEL-9", "quantity": 1}]}
        """,
        """
        {"customerId": "customer-42", "expiresAt": "2000-01-01T00:00:00Z",
         "items": [{"variantId": "PHN-PIXEL-9", "quantity": 1}]}
        """,
        """
        {"customerId": "customer-42", "expiresAt": "2999-01-01T00:00:00Z", "items": []}
        """,
        """
        {"customerId": "customer-42", "expiresAt": "2999-01-01T00:00:00Z",
         "items": [{"variantId": "PHN-PIXEL-9", "quantity": 0}]}
        """,
        """
        {"customerId": "customer-42", "expiresAt": "2999-01-01T00:00:00Z",
         "items": [{"variantId": "PHN-PIXEL-9"}]}
        """,
        "not json"
      })
  void anInvalidReservationIsABadRequest(String body) {
    var before = stockOf("PHN-PIXEL-9");

    reserve(body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(stockOf("PHN-PIXEL-9")).isEqualTo(before);
  }

  @Test
  void settlingNeedsACustomerId() {
    var reservation = reserved(item(newVariant(10), 1));

    for (var action : new String[] {"commit", "release"}) {
      http.post()
          .uri("/reservations/{id}/{action}", reservation.id(), action)
          .headers(h -> h.setBearerAuth(tokenToSettle(action)))
          .contentType(MediaType.APPLICATION_JSON)
          .body("{}")
          .exchange()
          .expectStatus()
          .isBadRequest();
    }
  }

  @Test
  void expiresAtIsKeptToTheMicrosecond() {
    var expiresAt = Instant.parse("2999-01-01T00:00:00.123456789Z");

    var reservation = reserved(expiresAt, item(newVariant(10), 1));

    assertThat(reservation.expiresAt()).isEqualTo("2999-01-01T00:00:00.123456Z");
  }
}
