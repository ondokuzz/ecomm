package com.ecomm.commons.events.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;

/**
 * Copies a consumer group's dead events from its dead-letter topic to its redrive topic, which its
 * listeners also consume with the same handler (see {@link DeadLetterTopics}), once their cause is
 * fixed. Each record is copied as it is, key, value and headers, so a redriven event that fails
 * again is dead-lettered again with the topic, partition and offset it first came from.
 *
 * <p>Each run copies what was dead-lettered since the last run, up to the end of the dead-letter
 * topic when it started, and remembers how far it got as the offsets of the consumer group {@code
 * <group>.dlt-redrive}. An event copied twice, after a run that failed before it could commit, is
 * applied once, since a listener applies each event only if it is newer than what it holds.
 *
 * <p>{@code make dlt-redrive GROUP=<group>} runs it in the image of the service that owns the
 * group: {@code DeadLetterRedrive <bootstrap servers> <group>}.
 */
public final class DeadLetterRedrive {

  private static final Duration POLL = Duration.ofMillis(500);

  private DeadLetterRedrive() {}

  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.println("Usage: DeadLetterRedrive <bootstrap servers> <consumer group>");
      System.exit(2);
    }
    quietKafkaClientLogs();
    var group = args[1];
    var copied = redrive(args[0], group);
    System.out.printf(
        "Redrove %d events from %s to %s%n",
        copied, DeadLetterTopics.deadLetterTopicOf(group), DeadLetterTopics.redriveTopicOf(group));
  }

  /**
   * Copies the group's dead events since the last run to its redrive topic.
   *
   * @return how many were copied
   */
  public static int redrive(String bootstrapServers, String group) {
    var deadLetters = DeadLetterTopics.deadLetterTopicOf(group);
    var redrive = DeadLetterTopics.redriveTopicOf(group);
    try (var consumer =
            new KafkaConsumer<>(consumerConfig(bootstrapServers, group), bytes(), bytes());
        var producer =
            new KafkaProducer<>(
                Map.<String, Object>of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    bootstrapServers,
                    ProducerConfig.ACKS_CONFIG,
                    "all"),
                new ByteArraySerializer(),
                new ByteArraySerializer())) {
      var partitions =
          consumer.partitionsFor(deadLetters).stream()
              .map(p -> new TopicPartition(deadLetters, p.partition()))
              .toList();
      if (partitions.isEmpty()) {
        throw new IllegalStateException("There is no topic " + deadLetters);
      }
      consumer.assign(partitions);
      var ends = consumer.endOffsets(partitions);
      var sent = new ArrayList<Future<RecordMetadata>>();
      while (partitions.stream().anyMatch(p -> consumer.position(p) < ends.get(p))) {
        for (var dead : consumer.poll(POLL)) {
          var end = ends.get(new TopicPartition(dead.topic(), dead.partition()));
          if (dead.offset() < end) {
            sent.add(
                producer.send(
                    new ProducerRecord<>(redrive, null, dead.key(), dead.value(), dead.headers())));
          }
        }
      }
      producer.flush();
      for (var each : sent) {
        each.get(); // Fails the run, uncommitted, if any copy wasn't written.
      }
      var reached = new HashMap<TopicPartition, OffsetAndMetadata>();
      for (var partition : partitions) {
        reached.put(partition, new OffsetAndMetadata(ends.get(partition)));
      }
      consumer.commitSync(reached);
      return sent.size();
    } catch (ExecutionException e) {
      throw new IllegalStateException("Could not copy to " + redrive, e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static Map<String, Object> consumerConfig(String bootstrapServers, String group) {
    return Map.of(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        bootstrapServers,
        ConsumerConfig.GROUP_ID_CONFIG,
        group + ".dlt-redrive",
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
        false,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
        "earliest");
  }

  private static ByteArrayDeserializer bytes() {
    return new ByteArrayDeserializer();
  }

  /** Without Spring Boot's logging set up, Logback would log every client's DEBUG lines. */
  private static void quietKafkaClientLogs() {
    LoggingSystem.get(DeadLetterRedrive.class.getClassLoader()).setLogLevel(null, LogLevel.WARN);
  }
}
