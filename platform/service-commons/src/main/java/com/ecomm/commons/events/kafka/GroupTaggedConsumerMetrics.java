package com.ecomm.commons.events.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.ArrayList;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.KafkaException;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.MicrometerConsumerListener;

/**
 * Kafka's consumer client metrics, its lag per topic and partition among them, as Boot binds them,
 * with the consumer group as a {@code group} tag too; Kafka's own metrics only name the client. The
 * tag goes in with the client's other tags, rather than through a {@code MeterFilter}, because
 * Micrometer's Kafka binder compares tags to tell its meters apart.
 */
class GroupTaggedConsumerMetrics<K, V> extends MicrometerConsumerListener<K, V> {

  private GroupTaggedConsumerMetrics(MeterRegistry registry) {
    super(registry);
  }

  /** Replaces the listener Boot adds to {@code factory}, if it added one. */
  static <K, V> void replaceBootListenerOn(
      DefaultKafkaConsumerFactory<K, V> factory, MeterRegistry registry) {
    var boots =
        factory.getListeners().stream()
            .filter(listener -> listener.getClass() == MicrometerConsumerListener.class)
            .toList();
    if (!boots.isEmpty()) {
      boots.forEach(factory::removeListener);
      factory.addListener(new GroupTaggedConsumerMetrics<>(registry));
    }
  }

  @Override
  protected MeterBinder createClientMetrics(Consumer<K, V> consumer, List<Tag> tags) {
    var withGroup = new ArrayList<>(tags);
    try {
      // Called as the consumer is created, before any other thread uses it.
      withGroup.add(Tag.of("group", consumer.groupMetadata().groupId()));
    } catch (KafkaException e) {
      // A consumer without a group: its metrics go untagged.
    }
    return super.createClientMetrics(consumer, withGroup);
  }
}
