package com.ecomm.commons.events.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RetryListener;

/**
 * Counts each retry of a failed event as {@code ecomm.events.retried}, tagged with the consumer
 * group and the topic the event was first read from. A failure is retried until the {@code retries}
 * in {@link ConsumerProperties} are spent; the last failure isn't counted, since the event is
 * dead-lettered instead, and nor is a failure that isn't retried, such as an event that can't be
 * read.
 */
class RetriedEventCounter implements RetryListener {

  private final MeterRegistry registry;
  private final int retries;
  private final Predicate<Exception> retryable;

  RetriedEventCounter(MeterRegistry registry, int retries, Predicate<Exception> retryable) {
    this.registry = registry;
    this.retries = retries;
    this.retryable = retryable;
  }

  @Override
  public void failedDelivery(ConsumerRecord<?, ?> record, Exception failure, int deliveryAttempt) {
    if (deliveryAttempt <= retries && retryable.test(failure)) {
      EventCounters.increment(
          registry, "ecomm.events.retried", "Retries of events a listener failed on", record);
    }
  }
}
