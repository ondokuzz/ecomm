package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.inventory.application.port.in.ReleaseExpiredReservationsUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Every change to a Variant's On-hand, its available Stock or whether it is stocked publishes one
 * {@code inventory.stock} event: keyed by the Variant's ID, valid against its schema, carrying the
 * request's Correlation ID and a higher version than the last. A refused change publishes nothing.
 */
class StockEventsApiTest extends InventoryApiTest {

  @Autowired ReleaseExpiredReservationsUseCase sweeper;

  @Test
  void settingOnHandPublishesTheVariantsStock() {
    var variant = "TEST-" + UUID.randomUUID();
    http.put()
        .uri("/stock/{variantId}", variant)
        .headers(h -> h.setBearerAuth(staffToken()))
        .header("X-Correlation-Id", "stock-events-set-1")
        .contentType(MediaType.APPLICATION_JSON)
        .body(onHandBody(12))
        .exchange()
        .expectStatus()
        .isCreated();

    var records = StockEvents.keyed(variant, 1);

    assertThat(records).hasSize(1);
    var record = records.getFirst();
    assertThat(StockEvents.schemaViolationsOf(record)).isEmpty();
    var header = record.headers().lastHeader("X-Correlation-Id");
    assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo("stock-events-set-1");
    var event = StockEvents.valueOf(record);
    assertThat(event.get("variantId").asText()).isEqualTo(variant);
    assertThat(event.get("change").asText()).isEqualTo("ADJUSTED");
    assertThat(event.get("version").asLong()).isPositive();
    assertThat(event.at("/stock/onHand").asInt()).isEqualTo(12);
    assertThat(event.at("/stock/available").asInt()).isEqualTo(12);
    assertThat(event.at("/stock/stocked").asBoolean()).isTrue();
  }

  @Test
  void eachChangePublishesTheStockAsItNowIsWithAHigherVersion() {
    var variant = newVariant(10);
    release(reserved(item(variant, 3)).id()).expectStatus().isOk();
    commit(reserved(item(variant, 4)).id()).expectStatus().isOk();
    setOnHand(staffToken(), variant, onHandBody(8)).expectStatus().isOk();

    var records = StockEvents.keyed(variant, 6);

    assertThat(records).allSatisfy(r -> assertThat(StockEvents.schemaViolationsOf(r)).isEmpty());
    var events = valuesOf(records);
    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("ADJUSTED", "RESERVED", "RELEASED", "RESERVED", "COMMITTED", "ADJUSTED");
    assertThat(events)
        .extracting(e -> e.at("/stock/onHand").asInt())
        .containsExactly(10, 10, 10, 10, 6, 8);
    assertThat(events)
        .extracting(e -> e.at("/stock/available").asInt())
        .containsExactly(10, 7, 10, 6, 6, 8);
    assertThat(events)
        .extracting(e -> e.get("version").asLong())
        .doesNotHaveDuplicates()
        .isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  void reservingCommittingAndReleasingCarryCheckoutsCorrelationId() {
    var variant = newVariant(10);
    settleWith(reserveWith(variant, "stock-events-reserve-1"), "commit", "stock-events-commit-1");
    settleWith(reserveWith(variant, "stock-events-reserve-2"), "release", "stock-events-release-1");

    var records = StockEvents.keyed(variant, 5);

    assertThat(records)
        .extracting(r -> new String(r.headers().lastHeader("X-Correlation-Id").value()))
        .endsWith(
            "stock-events-reserve-1",
            "stock-events-commit-1",
            "stock-events-reserve-2",
            "stock-events-release-1");
  }

  @Test
  void aReservationPublishesEachOfItsVariants() {
    var pixel = newVariant(10);
    var buds = newVariant(5);

    reserved(item(pixel, 2), item(buds, 1));

    assertThat(availableIn(StockEvents.keyed(pixel, 2).getLast())).isEqualTo(8);
    assertThat(availableIn(StockEvents.keyed(buds, 2).getLast())).isEqualTo(4);
  }

  @Test
  void aFailedReservationPublishesNothing() {
    var plenty = newVariant(10);
    var scarce = newVariant(1);

    reserve(reservationBody(CUSTOMER, inFifteenMinutes(), item(plenty, 1), item(scarce, 2)))
        .expectStatus()
        .isEqualTo(409);
    setOnHand(staffToken(), scarce, onHandBody(2)).expectStatus().isOk();

    assertThat(StockEvents.keyed(plenty, 2, Duration.ofSeconds(5))).hasSize(1);
    assertThat(valuesOf(StockEvents.keyed(scarce, 2)))
        .extracting(e -> e.get("change").asText())
        .containsExactly("ADJUSTED", "ADJUSTED");
  }

  @Test
  void settingOnHandToWhatItIsPublishesNothing() {
    var variant = newVariant(10);

    setOnHand(staffToken(), variant, onHandBody(10)).expectStatus().isOk();

    assertThat(StockEvents.keyed(variant, 2, Duration.ofSeconds(5))).hasSize(1);
  }

  @Test
  void removingAVariantPublishesThatItIsNoLongerStocked() {
    var variant = newVariant(6);

    removeStock(staffToken(), variant).expectStatus().isNoContent();
    setOnHand(staffToken(), variant, onHandBody(2)).expectStatus().isCreated();

    var events = valuesOf(StockEvents.keyed(variant, 3));
    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("ADJUSTED", "REMOVED", "ADJUSTED");
    var removed = events.get(1);
    assertThat(removed.at("/stock/stocked").asBoolean()).isFalse();
    assertThat(removed.at("/stock/onHand").asInt()).isZero();
    assertThat(removed.at("/stock/available").asInt()).isZero();
    assertThat(events.getLast().at("/stock/stocked").asBoolean()).isTrue();
    assertThat(events)
        .extracting(e -> e.get("version").asLong())
        .isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  void anExpiredReservationsStockIsPublishedByTheNextSweep() {
    var variant = newVariant(10);
    reserved(clock.now().plusSeconds(60), item(variant, 4));
    clock.advanceBy(Duration.ofSeconds(60));

    assertThat(StockEvents.keyed(variant, 3, Duration.ofSeconds(3))).hasSize(2);

    sweeper.releaseExpired();

    var records = StockEvents.keyed(variant, 3);
    assertThat(StockEvents.schemaViolationsOf(records.getLast())).isEmpty();
    var events = valuesOf(records);
    var swept = events.getLast();
    assertThat(swept.get("change").asText()).isEqualTo("RELEASED");
    assertThat(swept.at("/stock/available").asInt()).isEqualTo(10);
    assertThat(swept.get("version").asLong()).isGreaterThan(events.get(1).get("version").asLong());
  }

  private String reserveWith(String variant, String correlationId) {
    return http.post()
        .uri("/reservations")
        .headers(h -> h.setBearerAuth(checkoutToken()))
        .header("X-Correlation-Id", correlationId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(reservationBody(CUSTOMER, inFifteenMinutes(), item(variant, 1)))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(ReservationView.class)
        .returnResult()
        .getResponseBody()
        .id();
  }

  private void settleWith(String reservationId, String action, String correlationId) {
    http.post()
        .uri("/reservations/{id}/{action}", reservationId, action)
        .headers(h -> h.setBearerAuth(checkoutToken()))
        .header("X-Correlation-Id", correlationId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(customerBody(CUSTOMER))
        .exchange()
        .expectStatus()
        .isOk();
  }

  private static int availableIn(ConsumerRecord<String, String> record) {
    return StockEvents.valueOf(record).at("/stock/available").asInt();
  }

  private static List<JsonNode> valuesOf(List<ConsumerRecord<String, String>> records) {
    return records.stream().map(StockEvents::valueOf).toList();
  }
}
