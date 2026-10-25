package com.ecomm.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A Cart from before abandonment was detected is indexed when Cart starts, as changed then, and is
 * abandoned a day later. Like {@link CartExpiryApiTest}, this looks past the HTTP seam: a Sprint 5
 * Cart is written as that stack held it, a bare hash, before this class's fresh context starts.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CartUpgradeApiTest extends CartApiTest {

  /** Runs as the context is built, before the application starts. */
  @DynamicPropertySource
  static void cartFromBeforeTheUpgrade(DynamicPropertyRegistry registry)
      throws IOException, InterruptedException {
    REDIS.execInContainer("redis-cli", "HSET", "cart:customer-sprint-5", "PHN-PIXEL-9", "1");
    REDIS.execInContainer("redis-cli", "EXPIRE", "cart:customer-sprint-5", "604800");
  }

  @Test
  void aCartFromBeforeTheUpgradeIsAbandonedADayAfterCartStarts() {
    clock.set(TestTimeSource.NOW.plus(Duration.ofHours(23)));
    abandonment.sweep();
    assertThat(AbandonedCartApiTest.eventsOf("customer-sprint-5")).isEmpty();

    clock.set(TestTimeSource.NOW.plus(Duration.ofHours(25)));
    abandonment.sweep();

    var events = AbandonedCartApiTest.eventsOf("customer-sprint-5");
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().get("cart").get("lastChangedAt").asString())
        .isEqualTo(TestTimeSource.NOW.toString());
    assertThat(itemsOf("customer-sprint-5")).containsExactly(new CartItemView("PHN-PIXEL-9", 1));
  }
}
