package com.ecomm.searchdiscovery;

import com.ecomm.commons.events.EventBackbone;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/** Test-only: writes to a topic as a producer would, bypassing every schema. */
final class Topics {

  private static final KafkaProducer<String, String> PRODUCER =
      new KafkaProducer<>(
          Map.of("bootstrap.servers", EventBackbone.bootstrapServers()),
          new StringSerializer(),
          new StringSerializer());

  private Topics() {}

  /** Sends {@code value} as it is, with the Correlation ID header if one is given. */
  static void send(String topic, String key, String value, String correlationId) {
    var record = new ProducerRecord<>(topic, key, value);
    if (correlationId != null) {
      record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));
    }
    try {
      PRODUCER.send(record).get();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
