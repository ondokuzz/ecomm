package com.ecomm.fulfillmentshipping;

import com.ecomm.commons.events.EventBackbone;
import com.networknt.schema.InputFormat;
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
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only: reads the Shipment topic as any consumer would, and checks each event against the
 * topic's schema in {@code platform/event-schemas}, the one the registry holds.
 */
final class ShipmentEvents {

  static final String TOPIC = "fulfillment-shipping.shipment";

  private static final JsonMapper JSON = new JsonMapper();
  private static final JsonSchema SCHEMA = loadSchema();

  /** How long to keep reading after the expected events, to catch any that shouldn't be there. */
  private static final Duration SETTLE = Duration.ofSeconds(2);

  private ShipmentEvents() {}

  /**
   * Every event keyed {@code shipmentId}, oldest first, once at least {@code atLeast} have arrived
   * (within 20 seconds) and no more arrive for a moment after.
   */
  static List<JsonNode> keyed(String shipmentId, int atLeast) {
    try (var consumer =
        new KafkaConsumer<String, String>(
            Map.of("bootstrap.servers", EventBackbone.bootstrapServers()),
            new StringDeserializer(),
            new StringDeserializer())) {
      var partitions =
          consumer.partitionsFor(TOPIC).stream()
              .map(p -> new TopicPartition(TOPIC, p.partition()))
              .toList();
      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);
      var found = new ArrayList<String>();
      var deadline = Instant.now().plusSeconds(20);
      Instant settled = null;
      while (Instant.now().isBefore(settled == null ? deadline : settled)) {
        for (var record : consumer.poll(Duration.ofMillis(200))) {
          if (shipmentId.equals(record.key())) {
            found.add(record.value());
          }
        }
        if (settled == null && found.size() >= atLeast) {
          settled = Instant.now().plus(SETTLE);
        }
      }
      return found.stream().map(ShipmentEvents::checked).toList();
    }
  }

  private static JsonNode checked(String value) {
    var errors = SCHEMA.validate(value, InputFormat.JSON);
    if (!errors.isEmpty()) {
      throw new AssertionError(TOPIC + " event breaks its schema: " + errors + "\n" + value);
    }
    return JSON.readTree(value);
  }

  private static JsonSchema loadSchema() {
    try (var in =
        ShipmentEvents.class
            .getClassLoader()
            .getResourceAsStream("event-schemas/" + TOPIC + ".json")) {
      return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
