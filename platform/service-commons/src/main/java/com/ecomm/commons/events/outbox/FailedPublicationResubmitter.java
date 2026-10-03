package com.ecomm.commons.events.outbox;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

/**
 * Sends failed publications again on a fixed interval, so an event whose send failed, say while
 * Kafka was down, goes out once it can without waiting for a restart. Until then it stays in the
 * {@code event_publication} table with status {@code FAILED}.
 */
class FailedPublicationResubmitter implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(FailedPublicationResubmitter.class);

  private final FailedEventPublications failed;
  private final Duration interval;
  private ScheduledExecutorService scheduler;

  FailedPublicationResubmitter(FailedEventPublications failed, Duration interval) {
    this.failed = failed;
    this.interval = interval;
  }

  @Override
  public void start() {
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("event-resubmitter").daemon().factory());
    scheduler.scheduleWithFixedDelay(
        this::resubmit, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
  }

  private void resubmit() {
    try {
      failed.resubmit(ResubmissionOptions.defaults());
    } catch (RuntimeException e) {
      log.warn("Resubmitting failed event publications failed", e);
    }
  }

  @Override
  public void stop() {
    scheduler.shutdownNow();
    scheduler = null;
  }

  @Override
  public boolean isRunning() {
    return scheduler != null;
  }
}
