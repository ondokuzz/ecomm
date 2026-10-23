package com.ecomm.catalog.adapter.in.startup;

import com.ecomm.catalog.application.port.in.PublishBackfillUseCase;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Publishes the Categories and Products stored before Catalog events once the service is up. After
 * the first start that finds any, there are none left, so it does nothing. A cold Couchbase can
 * time out its first query, so a failure is tried again after {@code first-retry-after}, doubling,
 * up to {@code attempts} in all; then it fails the start-up. Each try skips what an earlier one
 * published.
 */
@Component
class BackfillOnStartup {

  private static final Logger log = LoggerFactory.getLogger(BackfillOnStartup.class);

  private final PublishBackfillUseCase backfill;
  private final int attempts;
  private final Duration firstRetryAfter;

  BackfillOnStartup(
      PublishBackfillUseCase backfill,
      @Value("${ecomm.catalog.backfill.attempts}") int attempts,
      @Value("${ecomm.catalog.backfill.first-retry-after}") Duration firstRetryAfter) {
    this.backfill = backfill;
    this.attempts = attempts;
    this.firstRetryAfter = firstRetryAfter;
  }

  @EventListener(ApplicationReadyEvent.class)
  void publishBackfill() {
    var wait = firstRetryAfter;
    for (var attempt = 1; ; attempt++) {
      try {
        var published = backfill.publishBackfill();
        if (published > 0) {
          log.info("Published {} existing Categories and Products as backfilled", published);
        }
        return;
      } catch (RuntimeException e) {
        if (attempt >= attempts) {
          throw e;
        }
        log.warn(
            "Backfill attempt {} of {} failed, trying again in {}: {}",
            attempt,
            attempts,
            wait,
            e.getMessage());
        sleep(wait);
        wait = wait.multipliedBy(2);
      }
    }
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting to retry the backfill", e);
    }
  }
}
