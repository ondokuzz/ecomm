package com.ecomm.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

/**
 * {@code cart_abandoned_total} is on {@code /actuator/prometheus} at 0 from the start, before any
 * Cart is abandoned, so that Grafana's {@code increase()} counts the first one; then it counts.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AbandonedCartMetricsApiTest extends CartApiTest {

  private static final Pattern ABANDONED =
      Pattern.compile("(?m)^cart_abandoned_total\\{[^}]*\\} (\\S+)$");

  @Test
  void abandonedCartsAreCountedFromZero() {
    assertThat(abandonedTotal()).isZero();

    setQuantity("customer-counted", "PHN-PIXEL-9", 1).expectStatus().isOk();
    clock.set(TestTimeSource.NOW.plus(Duration.ofHours(25)));
    abandonment.sweep();

    assertThat(abandonedTotal()).isGreaterThanOrEqualTo(1);
  }

  private double abandonedTotal() {
    var scrape =
        http.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    var matcher = ABANDONED.matcher(scrape);
    assertThat(matcher.find()).as("cart_abandoned_total in the scrape").isTrue();
    return Double.parseDouble(matcher.group(1));
  }
}
