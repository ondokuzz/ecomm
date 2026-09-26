package com.ecomm.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * A Cart lasts 7 days after its last change. Redis enforces that with a TTL on the Customer's hash,
 * {@code cart:<Customer ID>}, which these tests read straight from the container. They are the one
 * place Cart tests look past the HTTP seam: the issue asks for the TTL to be tested, and a 7-day
 * lapse can't be observed through the API.
 */
class CartExpiryApiTest extends CartApiTest {

  private static final Duration SEVEN_DAYS = Duration.ofDays(7);

  @Test
  void aCartExpiresSevenDaysAfterItIsCreated() throws Exception {
    setQuantity("customer-ttl", "PHN-PIXEL-9", 1).expectStatus().isOk();

    assertThat(ttlOf("customer-ttl")).isBetween(SEVEN_DAYS.minusMinutes(1), SEVEN_DAYS);
  }

  @Test
  void settingAQuantityRestartsTheSevenDays() throws Exception {
    setQuantity("customer-ttl-set", "PHN-PIXEL-9", 1).expectStatus().isOk();
    redisCli("EXPIRE", "cart:customer-ttl-set", "60");

    setQuantity("customer-ttl-set", "PHN-PIXEL-9", 2).expectStatus().isOk();

    assertThat(ttlOf("customer-ttl-set")).isBetween(SEVEN_DAYS.minusMinutes(1), SEVEN_DAYS);
  }

  @Test
  void removingAVariantRestartsTheSevenDays() throws Exception {
    setQuantity("customer-ttl-remove", "PHN-PIXEL-9", 1).expectStatus().isOk();
    setQuantity("customer-ttl-remove", "AUD-JBL-FLIP-6", 1).expectStatus().isOk();
    redisCli("EXPIRE", "cart:customer-ttl-remove", "60");

    http.delete()
        .uri("/cart/items/{variantId}", "PHN-PIXEL-9")
        .headers(h -> h.setBearerAuth(tokenFor("customer-ttl-remove")))
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(ttlOf("customer-ttl-remove")).isBetween(SEVEN_DAYS.minusMinutes(1), SEVEN_DAYS);
  }

  @Test
  void anExpiredCartIsEmpty() throws Exception {
    setQuantity("customer-expired", "PHN-PIXEL-9", 1).expectStatus().isOk();
    redisCli("PEXPIRE", "cart:customer-expired", "1");
    Thread.sleep(50);

    assertThat(itemsOf("customer-expired")).isEmpty();
  }

  private static Duration ttlOf(String customerId) throws Exception {
    return Duration.ofSeconds(Long.parseLong(redisCli("TTL", "cart:" + customerId)));
  }

  private static String redisCli(String... command) throws Exception {
    var args = new String[command.length + 1];
    args[0] = "redis-cli";
    System.arraycopy(command, 0, args, 1, command.length);
    return REDIS.execInContainer(args).getStdout().strip();
  }
}
