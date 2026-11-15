package com.ecomm.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Notifications reads a named Customer's Cart, before it sends a Cart reminder, to check the Cart
 * is unchanged since it was abandoned: its lines, when it last changed, and that change as a
 * version. Only Notifications may read it.
 */
class NotificationsCartReadApiTest extends CartApiTest {

  private static final String NOTIFICATIONS = FakeKeycloak.token("notifications", "NOTIFICATIONS");

  record InternalCartView(List<CartItemView> items, Instant lastChangedAt, long version) {}

  @Test
  void notificationsReadsTheNamedCustomersCartWithItsLastChange() {
    setQuantity("customer-ida", "PHN-PIXEL-9", 2).expectStatus().isOk();
    var changedAt = TestTimeSource.NOW.plus(Duration.ofMinutes(5));
    clock.set(changedAt);
    setQuantity("customer-ida", "AUD-JBL-FLIP-6", 1).expectStatus().isOk();

    var cart = read(NOTIFICATIONS, "customer-ida").expectStatus().isOk();

    var body = cart.expectBody(InternalCartView.class).returnResult().getResponseBody();
    assertThat(body.items())
        .containsExactly(new CartItemView("AUD-JBL-FLIP-6", 1), new CartItemView("PHN-PIXEL-9", 2));
    assertThat(body.lastChangedAt()).isEqualTo(Instant.parse("2026-10-25T12:05:00Z"));
    assertThat(body.version()).isEqualTo(changedAt.toEpochMilli());
  }

  @Test
  void anAbandonedCartIsReadWithTheChangeItWasAbandonedAt() {
    setQuantity("customer-jo", "PHN-PIXEL-9", 1).expectStatus().isOk();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE));
    abandonment.sweep();

    var body =
        read(NOTIFICATIONS, "customer-jo")
            .expectStatus()
            .isOk()
            .expectBody(InternalCartView.class)
            .returnResult()
            .getResponseBody();

    assertThat(body.items()).containsExactly(new CartItemView("PHN-PIXEL-9", 1));
    assertThat(body.lastChangedAt()).isEqualTo(TestTimeSource.NOW);
  }

  @Test
  void aCustomerWithNoCartIsNotFound() {
    read(NOTIFICATIONS, "customer-nobody")
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void anEmptiedCartIsNotFound() {
    setQuantity("customer-kim", "PHN-PIXEL-9", 1).expectStatus().isOk();
    http.delete()
        .uri("/cart/items/{variantId}", "PHN-PIXEL-9")
        .headers(h -> h.setBearerAuth(tokenFor("customer-kim")))
        .exchange()
        .expectStatus()
        .isOk();

    read(NOTIFICATIONS, "customer-kim").expectStatus().isNotFound();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "ORCHESTRATION", "STAFF"})
  void onlyNotificationsReadsANamedCustomersCart(String role) {
    setQuantity("customer-lee", "PHN-PIXEL-9", 1).expectStatus().isOk();

    read(FakeKeycloak.token("customer-lee", role), "customer-lee").expectStatus().isForbidden();
  }

  private RestTestClient.ResponseSpec read(String token, String customerId) {
    return http.get()
        .uri("/internal/carts/{customerId}", customerId)
        .headers(h -> h.setBearerAuth(token))
        .exchange();
  }
}
