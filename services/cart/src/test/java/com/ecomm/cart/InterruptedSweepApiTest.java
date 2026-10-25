package com.ecomm.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * A Cart is marked abandoned only once its event is acknowledged. A sweep that stops in between
 * leaves it unmarked, and the next sweep publishes the same event, with the same ID, for a consumer
 * to dedupe. Sweeps take a lock, so two at once publish an abandonment once.
 */
@Import(InterruptiblePublisher.class)
class InterruptedSweepApiTest extends CartApiTest {

  @Autowired InterruptiblePublisher publisher;

  @Test
  void aSweepStoppedBetweenPublishingAndMarkingPublishesTheSameEventAgain() {
    setQuantity("customer-crash", "PHN-PIXEL-9", 1).expectStatus().isOk();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE));
    publisher.crashAfterPublishing("customer-crash");
    assertThatThrownBy(abandonment::sweep).isInstanceOf(IllegalStateException.class);

    abandonment.sweep();
    abandonment.sweep();

    var events = AbandonedCartApiTest.eventsOf("customer-crash");
    assertThat(events).hasSize(2);
    assertThat(events.get(1).get("eventId").asString())
        .isEqualTo(events.getFirst().get("eventId").asString());
  }

  @Test
  void twoSweepsAtOncePublishAnAbandonmentOnce() throws Exception {
    setQuantity("customer-race", "PHN-PIXEL-9", 1).expectStatus().isOk();
    clock.set(TestTimeSource.NOW.plus(A_DAY_AND_A_MINUTE));
    var hold = publisher.holdBeforePublishing("customer-race");
    var first = CompletableFuture.supplyAsync(abandonment::sweep);
    hold.awaitReached();

    var second = abandonment.sweep();
    hold.release();
    first.get();

    assertThat(second).isZero();
    assertThat(AbandonedCartApiTest.eventsOf("customer-race")).hasSize(1);
  }
}
