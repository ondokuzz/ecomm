package com.ecomm.template;

import com.ecomm.commons.events.EventBackbone;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Test-only: reads a topic as any consumer would, and writes to it bypassing every schema. */
final class Topics {

  private Topics() {}

  /** The first record on {@code topic} keyed {@code key} within {@code within}, if any. */
  static Optional<ConsumerRecord<String, String>> recordKeyed(
      String topic, String key, Duration within) {
    try (var consumer =
        new KafkaConsumer<String, String>(
            Map.of("bootstrap.servers", EventBackbone.bootstrapServers()),
            new StringDeserializer(),
            new StringDeserializer())) {
      var partitions =
          consumer.partitionsFor(topic).stream()
              .map(p -> new TopicPartition(topic, p.partition()))
              .toList();
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);
      var deadline = Instant.now().plus(within);
      while (Instant.now().isBefore(deadline)) {
        for (var record : consumer.poll(Duration.ofMillis(200))) {
          if (key.equals(record.key())) {
            return Optional.of(record);
          }
        }
      }
      return Optional.empty();
    }
  }

  /** Sends {@code value} as it is, with the Correlation ID header if one is given. */
  static void send(String topic, String key, String value, String correlationId) {
    try (var producer =
        new KafkaProducer<String, String>(
            Map.of("bootstrap.servers", EventBackbone.bootstrapServers()),
            new StringSerializer(),
            new StringSerializer())) {
      var record = new ProducerRecord<>(topic, key, value);
      if (correlationId != null) {
        record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
      }
      producer.send(record).get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
