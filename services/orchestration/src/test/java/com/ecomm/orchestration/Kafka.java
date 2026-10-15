package com.ecomm.orchestration;

import com.ecomm.commons.events.kafka.DeadLetterTopics;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Test-only: a Kafka broker for Orchestration's tests, one per test JVM. Orchestration only
 * consumes, so unlike {@code EventBackbone} it runs no schema registry. As in the compose stack,
 * the broker creates no topics; each topic has one partition, so events are read in the order they
 * were sent.
 */
final class Kafka {

  private static final KafkaContainer BROKER =
      new KafkaContainer("apache/kafka:4.3.1").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

  static {
    BROKER.start();
  }

  private Kafka() {}

  static String bootstrapServers() {
    return BROKER.getBootstrapServers();
  }

  /** Creates a compacted topic, as the stack's are. */
  static void createTopic(String topic) {
    createTopic(topic, "compact");
  }

  /** Creates the consumer group's dead-letter and redrive topics. */
  static void createDeadLetterTopicsOf(String group) {
    createTopic(DeadLetterTopics.deadLetterTopicOf(group), "delete");
    createTopic(DeadLetterTopics.redriveTopicOf(group), "delete");
  }

  private static void createTopic(String topic, String cleanupPolicy) {
    try (var admin = Admin.create(Map.of("bootstrap.servers", bootstrapServers()))) {
      admin
          .createTopics(
              List.of(
                  new NewTopic(topic, 1, (short) 1)
                      .configs(Map.of("cleanup.policy", cleanupPolicy))))
          .all()
          .get();
    } catch (ExecutionException e) {
      if (!(e.getCause() instanceof TopicExistsException)) {
        throw new IllegalStateException("Could not create " + topic, e.getCause());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** Every record on {@code topic} keyed {@code key}, from its start to its end now. */
  static List<ConsumerRecord<String, String>> recordsKeyed(String topic, String key) {
    try (var consumer =
        new KafkaConsumer<String, String>(
            Map.of("bootstrap.servers", bootstrapServers()),
            new StringDeserializer(),
            new StringDeserializer())) {
      var partitions = List.of(new TopicPartition(topic, 0));
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);
      var end = consumer.endOffsets(partitions).get(partitions.getFirst());
      var keyed = new ArrayList<ConsumerRecord<String, String>>();
      while (consumer.position(partitions.getFirst()) < end) {
        for (var record : consumer.poll(Duration.ofMillis(200))) {
          if (key.equals(record.key())) {
            keyed.add(record);
          }
        }
      }
      return keyed;
    }
  }
}
