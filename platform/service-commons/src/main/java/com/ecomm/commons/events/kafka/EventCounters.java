package com.ecomm.commons.events.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.KafkaUtils;

/**
 * The counters of what happened to events, {@code ecomm.events.*}, each tagged with the consumer
 * group and the topic the event was first read from, so a redriven event counts under its original
 * topic rather than {@code <group>.redrive}.
 */
final class EventCounters {

  private EventCounters() {}

  /** Call on the consumer thread, where the listener container sets the group. */
  static void increment(
      MeterRegistry registry, String name, String description, ConsumerRecord<?, ?> record) {
    Counter.builder(name)
        .description(description)
        .tag("group", String.valueOf(KafkaUtils.getConsumerGroupId()))
        .tag("topic", DeadLetterTopics.originalTopicOf(record))
        .register(registry)
        .increment();
  }
}
