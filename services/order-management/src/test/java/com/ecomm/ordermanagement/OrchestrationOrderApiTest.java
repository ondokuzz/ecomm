package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The checkout Saga places Orders and changes their status with Orchestration's own token, each
 * command under an {@code Idempotency-Key}: a repeat replays the first response, and every change
 * it makes is recorded as {@code ORCHESTRATION}'s. Checkout keeps both commands, without a key,
 * until it moves onto the Saga.
 */
class OrchestrationOrderApiTest extends OrderApiTest {

  private static final String ORCHESTRATION = FakeKeycloak.token("orchestration", "ORCHESTRATION");

  @Test
  void orchestrationPlacesAnOrderAndARepeatReplaysIt() {
    var key = newKey();

    var first = orchestrationPlaces(key, twoLineOrder(CUSTOMER)).returnResult(OrderView.class);
    var repeat = orchestrationPlaces(key, twoLineOrder(CUSTOMER)).returnResult(OrderView.class);

    assertThat(first.getStatus().value()).isEqualTo(201);
    var order = first.getResponseBody();
    assertThat(order.statusHistory())
        .containsExactly(new HistoryEntryView("PLACED", order.placedAt(), "ORCHESTRATION", false));
    assertThat(repeat.getStatus().value()).isEqualTo(201);
    assertThat(repeat.getResponseHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
    assertThat(repeat.getResponseBody()).isEqualTo(order);
    assertThat(repeat.getResponseHeaders().getLocation())
        .isEqualTo(first.getResponseHeaders().getLocation());
  }

  @Test
  void orchestrationMovesAnOrderAlongAndTheHistoryAndEventsSayItDid() {
    var id =
        orchestrationPlaces(newKey(), twoLineOrder(CUSTOMER))
            .returnResult(OrderView.class)
            .getResponseBody()
            .id();

    var paid =
        orchestrationChanges(newKey(), id, statusChange(CUSTOMER, "PAID"))
            .expectStatus()
            .isOk()
            .expectBody(OrderView.class)
            .returnResult()
            .getResponseBody();

    assertThat(paid.statusHistory())
        .extracting(HistoryEntryView::changedBy)
        .containsExactly("ORCHESTRATION", "ORCHESTRATION");
    var events = OrderEvents.keyed(id, 2).stream().map(OrderEvents::valueOf).toList();
    assertThat(events).hasSize(2);
    var history = events.getLast().get("order").get("statusHistory");
    assertThat(history.get(1).get("status").asText()).isEqualTo("PAID");
    assertThat(history.get(1).get("changedBy").asText()).isEqualTo("ORCHESTRATION");
  }

  @Test
  void aRepeatedStatusChangeReplaysWithoutChangingTheOrderAgain() {
    var id = placed().id();
    var key = newKey();
    orchestrationChanges(newKey(), id, statusChange(CUSTOMER, "PAID")).expectStatus().isOk();
    var cancelled =
        orchestrationChanges(key, id, statusChange(CUSTOMER, "CANCELLED"))
            .returnResult(OrderView.class)
            .getResponseBody();

    var repeat = orchestrationChanges(key, id, statusChange(CUSTOMER, "CANCELLED"));

    repeat
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Idempotent-Replayed", "true")
        .expectBody(OrderView.class)
        .isEqualTo(cancelled);
    assertThat(cancelled.statusHistory())
        .extracting(HistoryEntryView::status, HistoryEntryView::changedBy)
        .containsExactly(
            tuple("PLACED", "CHECKOUT"),
            tuple("PAID", "ORCHESTRATION"),
            tuple("CANCELLED", "ORCHESTRATION"));
  }

  @Test
  void orchestrationNeedsAKeyForEitherCommand() {
    var id = placed().id();

    orchestrationPlaces(null, twoLineOrder(CUSTOMER))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("idempotencyKeyRequired");
    orchestrationChanges(null, id, statusChange(CUSTOMER, "PAID"))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("idempotencyKeyRequired");
  }

  @Test
  void checkoutStillPlacesAndChangesOrdersWithoutAKey() {
    var order = placed();

    changeStatus(order.id(), "PAID").expectStatus().isOk();

    assertThat(order.statusHistory().getFirst().changedBy()).isEqualTo("CHECKOUT");
  }

  private RestTestClient.ResponseSpec orchestrationPlaces(String key, String body) {
    return http.post()
        .uri("/orders")
        .headers(
            h -> {
              h.setBearerAuth(ORCHESTRATION);
              if (key != null) {
                h.set("Idempotency-Key", key);
              }
            })
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  private RestTestClient.ResponseSpec orchestrationChanges(String key, String id, String body) {
    return http.patch()
        .uri("/orders/{id}/status", id)
        .headers(
            h -> {
              h.setBearerAuth(ORCHESTRATION);
              if (key != null) {
                h.set("Idempotency-Key", key);
              }
            })
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  private static String newKey() {
    return "checkout-" + UUID.randomUUID() + ":run-1:activity";
  }
}
