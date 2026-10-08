package com.ecomm.payment;

import com.ecomm.commons.events.EventBackbone;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Test-only: reads the Payment topic as any consumer would, and checks each event against the
 * topic's schema in {@code platform/event-schemas}, the one the registry holds.
 */
final class PaymentEvents {

  private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
      new com.fasterxml.jackson.databind.ObjectMapper();
  private static final JsonSchema SCHEMA = loadSchema();

  /** How long to keep reading after the expected events, to catch any that shouldn't be there. */
  private static final Duration SETTLE = Duration.ofSeconds(2);

  private PaymentEvents() {}

  /**
   * Every event keyed {@code paymentId}, oldest first, once at least {@code atLeast} have arrived
   * (within 20 seconds) and no more arrive for a moment after.
   */
  static List<ConsumerRecord<String, String>> keyed(String paymentId, int atLeast) {
    return keyed(paymentId, atLeast, Duration.ofSeconds(20));
  }

  /** As {@link #keyed(String, int)}, waiting {@code within} for them. */
  static List<ConsumerRecord<String, String>> keyed(
      String paymentId, int atLeast, Duration within) {
    var topic = PaymentApiTest.PAYMENTS_TOPIC;
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
      var found = new ArrayList<ConsumerRecord<String, String>>();
      var deadline = Instant.now().plus(within);
      Instant settled = null;
      while (Instant.now().isBefore(settled == null ? deadline : settled)) {
        for (var record : consumer.poll(Duration.ofMillis(200))) {
          if (paymentId.equals(record.key())) {
            found.add(record);
          }
        }
        if (settled == null && found.size() >= atLeast) {
          settled = Instant.now().plus(SETTLE);
        }
      }
      return found;
    }
  }

  /** The event's value as JSON. */
  static com.fasterxml.jackson.databind.JsonNode valueOf(ConsumerRecord<String, String> record) {
    try {
      return JSON.readTree(record.value());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** What the topic's schema finds wrong with the event; empty when it matches. */
  static List<String> schemaViolationsOf(ConsumerRecord<String, String> record) {
    return SCHEMA.validate(valueOf(record)).stream().map(Object::toString).toList();
  }

  private static JsonSchema loadSchema() {
    var resource = "event-schemas/" + PaymentApiTest.PAYMENTS_TOPIC + ".json";
    try (var in = PaymentEvents.class.getClassLoader().getResourceAsStream(resource)) {
      return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
