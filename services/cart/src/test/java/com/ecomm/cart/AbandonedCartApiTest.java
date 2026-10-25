package com.ecomm.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Cart that holds Variants and goes unchanged for 24 hours is abandoned, once per abandonment: a
 * sweep publishes one {@code cart.cart} {@code ABANDONED} event for it. Each test changes its Cart
 * at a fixed time, moves the clock on and sweeps.
 */
class AbandonedCartApiTest extends CartApiTest {

  private static final JsonMapper JSON = new JsonMapper();

  @Test
  void aCartIdleForADayIsPublishedAsAbandoned() {
    setQuantity("customer-idle", "PHN-PIXEL-9", 2).expectStatus().isOk();
    setQuantity("customer-idle", "AUD-JBL-FLIP-6", 1).expectStatus().isOk();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE));

    abandonment.sweep();

    var events = eventsOf("customer-idle");
    assertThat(events).hasSize(1);
    var event = events.getFirst();
    assertThat(event.get("change").asString()).isEqualTo("ABANDONED");
    assertThat(event.get("customerId").asString()).isEqualTo("customer-idle");
    assertThat(event.get("cart").get("lastChangedAt").asString()).isEqualTo("2026-10-25T12:00:00Z");
    assertThat(event.get("version").asLong())
        .isEqualTo(Instant.parse("2026-10-25T12:00:00Z").toEpochMilli());
    assertThat(JSON.convertValue(event.get("cart").get("items"), CartItemView[].class))
        .containsExactly(new CartItemView("AUD-JBL-FLIP-6", 1), new CartItemView("PHN-PIXEL-9", 2));
  }

  @Test
  void aCartChangedWithinTheDayIsNotAbandoned() {
    setQuantity("customer-recent", "PHN-PIXEL-9", 1).expectStatus().isOk();
    clock.set(TestTimeSource.NOW.plus(Duration.ofHours(23)));

    abandonment.sweep();

    assertThat(eventsOf("customer-recent")).isEmpty();
  }

  @Test
  void aSecondSweepPublishesNothingMore() {
    setQuantity("customer-twice", "PHN-PIXEL-9", 1).expectStatus().isOk();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE));
    abandonment.sweep();

    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE.multipliedBy(2)));
    abandonment.sweep();

    assertThat(eventsOf("customer-twice")).hasSize(1);
  }

  @Test
  void aCartChangedAfterItWasAbandonedIsAbandonedAgain() {
    setQuantity("customer-back", "PHN-PIXEL-9", 1).expectStatus().isOk();
    var changedAgainAt = TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE);
    clock.set(changedAgainAt);
    abandonment.sweep();

    setQuantity("customer-back", "PHN-PIXEL-9", 3).expectStatus().isOk();
    clock.set(changedAgainAt.plus(Duration.ofHours(23)));
    abandonment.sweep();
    assertThat(eventsOf("customer-back")).hasSize(1);
    clock.set(changedAgainAt.plus(A_DAY_AND_A_MINUTE));
    abandonment.sweep();

    var events = eventsOf("customer-back");
    assertThat(events).hasSize(2);
    var second = events.get(1);
    assertThat(second.get("cart").get("lastChangedAt").asString())
        .isEqualTo("2026-10-26T12:01:00Z");
    assertThat(JSON.convertValue(second.get("cart").get("items"), CartItemView[].class))
        .containsExactly(new CartItemView("PHN-PIXEL-9", 3));
    assertThat(second.get("eventId").asString())
        .isNotEqualTo(events.getFirst().get("eventId").asString());
  }

  @Test
  void aCartEmptiedByRemovingItsLastVariantIsNeverAbandoned() {
    setQuantity("customer-emptied", "PHN-PIXEL-9", 1).expectStatus().isOk();
    http.delete()
        .uri("/cart/items/{variantId}", "PHN-PIXEL-9")
        .headers(h -> h.setBearerAuth(tokenFor("customer-emptied")))
        .exchange()
        .expectStatus()
        .isOk();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE));

    abandonment.sweep();

    assertThat(eventsOf("customer-emptied")).isEmpty();
  }

  @Test
  void aCartTheCustomerClearedIsNeverAbandoned() {
    setQuantity("customer-cleared", "PHN-PIXEL-9", 1).expectStatus().isOk();
    http.delete()
        .uri("/cart")
        .headers(h -> h.setBearerAuth(tokenFor("customer-cleared")))
        .exchange()
        .expectStatus()
        .isNoContent();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE));

    abandonment.sweep();

    assertThat(eventsOf("customer-cleared")).isEmpty();
  }

  @Test
  void aCartClearedByPayingIsNeverAbandoned() {
    setQuantity("customer-paid", "PHN-PIXEL-9", 1).expectStatus().isOk();
    http.post()
        .uri("/carts/clear")
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("orchestration", "ORCHESTRATION")))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"customerId\": \"customer-paid\"}")
        .exchange()
        .expectStatus()
        .isNoContent();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE));

    abandonment.sweep();

    assertThat(eventsOf("customer-paid")).isEmpty();
  }

  @Test
  void anAbandonedCartStillReadsAsItWas() {
    setQuantity("customer-reads", "PHN-PIXEL-9", 2).expectStatus().isOk();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE));
    abandonment.sweep();

    assertThat(itemsOf("customer-reads")).containsExactly(new CartItemView("PHN-PIXEL-9", 2));
  }

  @Test
  void anAbandonedCartEmptiedLaterIsGone() {
    setQuantity("customer-gone", "PHN-PIXEL-9", 2).expectStatus().isOk();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE));
    abandonment.sweep();

    http.delete()
        .uri("/cart/items/{variantId}", "PHN-PIXEL-9")
        .headers(h -> h.setBearerAuth(tokenFor("customer-gone")))
        .exchange()
        .expectStatus()
        .isOk();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE.multipliedBy(2)));
    abandonment.sweep();

    assertThat(itemsOf("customer-gone")).isEmpty();
    assertThat(eventsOf("customer-gone")).hasSize(1);
  }

  @Test
  void removingAVariantTheCartDoesNotHoldIsNoChange() {
    setQuantity("customer-no-op", "PHN-PIXEL-9", 1).expectStatus().isOk();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE));
    abandonment.sweep();

    http.delete()
        .uri("/cart/items/{variantId}", "AUD-JBL-FLIP-6")
        .headers(h -> h.setBearerAuth(tokenFor("customer-no-op")))
        .exchange()
        .expectStatus()
        .isOk();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE.multipliedBy(2)));
    abandonment.sweep();

    assertThat(eventsOf("customer-no-op")).hasSize(1);
  }

  static List<JsonNode> eventsOf(String customerId) {
    return EventBackbone.recordsKeyed(CART_TOPIC, customerId).stream()
        .map(record -> JSON.readTree(record.value()))
        .toList();
  }
}
