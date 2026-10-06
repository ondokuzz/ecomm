package com.ecomm.checkoutpricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Redis keeps a Checkout Session until its Reservation expires, 2 minutes after the session does,
 * so paying it late is a 410 rather than a 404. These tests read the TTLs straight from the
 * container: the one place Checkout tests look past the HTTP seam, since Redis's clock can't be
 * moved.
 */
class SessionStorageApiTest extends CheckoutApiTest {

  private static final Duration SEVENTEEN_MINUTES = Duration.ofMinutes(17);

  @Test
  void aSessionIsKeptUntilItsReservationExpires() throws Exception {
    stubSuccessfulCheckout();

    var sessionId = startedSessionId();

    assertThat(ttlOf("checkout-session:" + sessionId))
        .isBetween(SEVENTEEN_MINUTES.minusSeconds(10), SEVENTEEN_MINUTES);
    assertThat(ttlOf("checkout-customer:" + CUSTOMER_ID))
        .isBetween(SEVENTEEN_MINUTES.minusSeconds(10), SEVENTEEN_MINUTES);
  }

  /**
   * A session saved before sessions held their lines' Products and every Discount has neither, so
   * it can't be evaluated again; it reads as no session, and the Customer starts checkout afresh.
   */
  @Test
  void aSessionSavedBeforeSessionsHeldTheirDiscountsReadsAsNone() throws Exception {
    stubSuccessfulCheckout();
    var oldSession =
        """
        {"id": "0ld5e551-0000-4000-8000-000000000001", "customerId": "%s",
         "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 2,
                    "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}],
         "discount": {"couponCode": "WELCOME10", "amount": {"amountMinor": 15980, "currency": "EUR"}},
         "tax": {"amountMinor": 0, "currency": "EUR"}, "reservationId": "%s",
         "expiresAt": "2099-01-01T00:00:00Z"}
        """
            .formatted(CUSTOMER_ID, RESERVATION_ID);
    REDIS.execInContainer(
        "redis-cli", "SET", "checkout-session:0ld5e551-0000-4000-8000-000000000001", oldSession);
    REDIS.execInContainer(
        "redis-cli",
        "SET",
        "checkout-customer:" + CUSTOMER_ID,
        "0ld5e551-0000-4000-8000-000000000001");

    currentSession().expectStatus().isNotFound();
    applyCoupon("0ld5e551-0000-4000-8000-000000000001", "WELCOME10").expectStatus().isNotFound();
    startSession().expectStatus().isCreated();
  }

  private static Duration ttlOf(String key) throws Exception {
    var millis = REDIS.execInContainer("redis-cli", "PTTL", key).getStdout().strip();
    return Duration.ofMillis(Long.parseLong(millis));
  }
}
