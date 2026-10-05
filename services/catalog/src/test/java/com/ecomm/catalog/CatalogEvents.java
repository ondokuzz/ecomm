package com.ecomm.catalog;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.web.CorrelationId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Test-only: reads Catalog's topics as any consumer would, and checks each event against its
 * topic's schema in {@code platform/event-schemas}, the one the registry holds.
 */
final class CatalogEvents {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Map<String, JsonSchema> SCHEMAS = new ConcurrentHashMap<>();

  /** How long to keep reading after the expected events, to catch any that shouldn't be there. */
  private static final Duration SETTLE = Duration.ofSeconds(2);

  private CatalogEvents() {}

  /**
   * Every event on {@code topic} keyed {@code key}, oldest first, once at least {@code atLeast}
   * have arrived (within 20 seconds) and no more arrive for a moment after.
   */
  static List<ConsumerRecord<String, String>> keyed(String topic, String key, int atLeast) {
    return keyed(topic, key, atLeast, Duration.ofSeconds(20));
  }

  /** As {@link #keyed(String, String, int)}, waiting {@code within} for them. */
  static List<ConsumerRecord<String, String>> keyed(
      String topic, String key, int atLeast, Duration within) {
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
          if (key.equals(record.key())) {
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

  /** Each event's value as JSON, oldest first. */
  static List<JsonNode> valuesOf(List<ConsumerRecord<String, String>> records) {
    return records.stream().map(CatalogEvents::valueOf).toList();
  }

  static JsonNode valueOf(ConsumerRecord<String, String> record) {
    try {
      return JSON.readTree(record.value());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The Correlation ID the event carries in its header, or null. */
  static String correlationIdOf(ConsumerRecord<String, String> record) {
    var header = record.headers().lastHeader(CorrelationId.HEADER);
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }

  /** What the topic's schema finds wrong with the event; empty when it matches. */
  static List<String> schemaViolationsOf(ConsumerRecord<String, String> record) {
    return SCHEMAS
        .computeIfAbsent(record.topic(), CatalogEvents::loadSchema)
        .validate(valueOf(record))
        .stream()
        .map(Object::toString)
        .toList();
  }

  private static JsonSchema loadSchema(String topic) {
    var resource = "event-schemas/" + topic + ".json";
    try (var in = CatalogEvents.class.getClassLoader().getResourceAsStream(resource)) {
      return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
