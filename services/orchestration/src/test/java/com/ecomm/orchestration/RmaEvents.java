package com.ecomm.orchestration;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only: {@code returns-warranty.rma} events, as Returns & Warranty publishes them, with the
 * Correlation ID of the request behind them in the header. Each is checked against the topic's
 * schema in {@code platform/event-schemas} before it is sent.
 */
final class RmaEvents {

  static final String TOPIC = "returns-warranty.rma";
  static final String GROUP = "orchestration.rmas";

  private static final JsonMapper JSON = new JsonMapper();
  private static final KafkaProducer<String, String> PRODUCER =
      new KafkaProducer<>(
          Map.of("bootstrap.servers", Kafka.bootstrapServers()),
          new StringSerializer(),
          new StringSerializer());

  private RmaEvents() {}

  /** What an event says of the RMA beyond its ID, version and status. */
  record Rma(
      String reference,
      String orderId,
      String customerId,
      String variantId,
      int quantity,
      String kind,
      Long refundAmountMinor,
      Instant sendBackBy) {}

  /**
   * The RMA's event at {@code version}, in {@code status}, published for {@code change}: the step
   * that moved it, or {@code BACKFILLED}.
   */
  static void send(
      String rmaId, long version, String change, String status, Rma rma, String correlationId) {
    var snapshot = new LinkedHashMap<String, Object>();
    snapshot.put("reference", rma.reference());
    snapshot.put("orderId", rma.orderId());
    snapshot.put("customerId", rma.customerId());
    snapshot.put("variantId", rma.variantId());
    snapshot.put("productName", "Pixel 9");
    snapshot.put("quantity", rma.quantity());
    snapshot.put("serialNumbers", List.of());
    snapshot.put("kind", rma.kind());
    snapshot.put("reason", "It stopped charging");
    snapshot.put(
        "window",
        Map.of(
            "kind", "RETURN_PERIOD",
            "startsAt", "2026-10-01T09:30:00Z",
            "endsAt", "2026-10-15T09:30:00Z"));
    snapshot.put("status", status);
    snapshot.put("requestedAt", "2026-10-02T09:30:00Z");
    if (rma.refundAmountMinor() != null) {
      snapshot.put(
          "refundAmount", Map.of("amountMinor", rma.refundAmountMinor(), "currency", "EUR"));
    }
    if (rma.sendBackBy() != null) {
      snapshot.put("sendBackBy", rma.sendBackBy().toString());
      snapshot.put("warehouseId", "WH-BER");
    }
    snapshot.put(
        "decisions",
        List.of(
            Map.of(
                "status",
                "REQUESTED",
                "decidedBy",
                "CUSTOMER",
                "decidedAt",
                "2026-10-02T09:30:00Z")));
    var event = new LinkedHashMap<String, Object>();
    event.put("eventId", UUID.randomUUID().toString());
    event.put("occurredAt", Instant.now().toString());
    event.put("rmaId", rmaId);
    event.put("version", version);
    event.put("change", change);
    event.put("rma", snapshot);

    var json = JSON.writeValueAsString(event);
    var errors = schema().validate(json, InputFormat.JSON);
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(TOPIC + " event breaks its schema: " + errors);
    }
    var record = new ProducerRecord<>(TOPIC, rmaId, json);
    if (correlationId != null) {
      record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
    }
    try {
      PRODUCER.send(record).get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static com.networknt.schema.JsonSchema schema() {
    try (var in =
        RmaEvents.class.getClassLoader().getResourceAsStream("event-schemas/" + TOPIC + ".json")) {
      return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
