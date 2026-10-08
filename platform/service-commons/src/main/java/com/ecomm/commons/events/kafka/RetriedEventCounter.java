package com.ecomm.commons.events.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.KafkaUtils;

/**
 * Counts each retry of a failed event as {@code ecomm.events.retried}, tagged with the consumer
 * group and topic. A failure is retried until the {@code retries} in {@link ConsumerProperties} are
 * spent; the last failure isn't counted, since the event is skipped instead.
 */
class RetriedEventCounter implements RetryListener {

  private final MeterRegistry registry;
  private final int retries;

  RetriedEventCounter(MeterRegistry registry, int retries) {
    this.registry = registry;
    this.retries = retries;
  }

  @Override
  public void failedDelivery(ConsumerRecord<?, ?> record, Exception failure, int deliveryAttempt) {
    if (deliveryAttempt <= retries) {
      Counter.builder("ecomm.events.retried")
          .description("Retries of events a listener failed on")
          // The listener container sets it on the consumer thread this runs on.
          .tag("group", String.valueOf(KafkaUtils.getConsumerGroupId()))
          .tag("topic", record.topic())
          .register(registry)
          .increment();
    }
  }
}
