package com.ecomm.catalog.adapter.in.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.catalog.application.port.in.PublishBackfillUseCase;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * A backfill that fails, as a cold Couchbase's first query can time out, is tried again with
 * backoff; one that keeps failing still fails the start-up.
 */
class BackfillOnStartupTest {

  @Test
  void aBackfillThatFailsAtFirstIsTriedAgain() {
    var calls = new AtomicInteger();
    PublishBackfillUseCase backfill =
        () -> {
          if (calls.incrementAndGet() < 3) {
            throw new IllegalStateException("query timed out");
          }
          return 4;
        };

    new BackfillOnStartup(backfill, 5, Duration.ofMillis(1)).publishBackfill();

    assertThat(calls).hasValue(3);
  }

  @Test
  void aBackfillThatKeepsFailingFailsTheStartUp() {
    var calls = new AtomicInteger();
    PublishBackfillUseCase backfill =
        () -> {
          calls.incrementAndGet();
          throw new IllegalStateException("query timed out");
        };

    assertThatThrownBy(
            () -> new BackfillOnStartup(backfill, 3, Duration.ofMillis(1)).publishBackfill())
        .hasMessage("query timed out");
    assertThat(calls).hasValue(3);
  }
}
