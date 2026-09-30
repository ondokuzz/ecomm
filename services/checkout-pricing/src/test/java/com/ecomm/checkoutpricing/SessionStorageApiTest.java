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

  private static Duration ttlOf(String key) throws Exception {
    var millis = REDIS.execInContainer("redis-cli", "PTTL", key).getStdout().strip();
    return Duration.ofMillis(Long.parseLong(millis));
  }
}
