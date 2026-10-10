package com.ecomm.commons.events.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Counts each redriven event a listener handles as {@code ecomm.events.redriven}, tagged with the
 * consumer group and the topic the event was first consumed from. One that fails again is counted
 * as dead-lettered instead.
 */
class RedrivenEventCounter implements RecordInterceptor<Object, Object> {

  private final MeterRegistry registry;

  RedrivenEventCounter(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public ConsumerRecord<Object, Object> intercept(
      ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
    return record;
  }

  @Override
  public void success(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
    if (DeadLetterTopics.isRedrive(record)) {
      EventCounters.increment(
          registry, "ecomm.events.redriven", "Redriven events a listener handled", record);
    }
  }
}
