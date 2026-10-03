package com.ecomm.commons.events.kafka;

import com.ecomm.commons.web.CorrelationId;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The last resort for an event a listener couldn't handle: logs its {@code eventId}, topic,
 * partition and offset at error level, so it can be found and replayed, and lets the partition move
 * on. Dead-letter topics replace it in Sprint 4.
 */
class SkippedEventLogger implements ConsumerRecordRecoverer {

  private static final Logger log = LoggerFactory.getLogger(SkippedEventLogger.class);

  private final JsonMapper json;

  SkippedEventLogger(JsonMapper json) {
    this.json = json;
  }

  @Override
  public void accept(ConsumerRecord<?, ?> record, Exception failure) {
    try (var ignored =
        MDC.putCloseable(
            CorrelationId.MDC_KEY, CorrelationIdRecordInterceptor.correlationIdOf(record))) {
      log.error(
          "Skipped event {} from {} partition {} offset {}",
          eventIdOf(record),
          record.topic(),
          record.partition(),
          record.offset(),
          failure);
    }
  }

  /** The event's {@code eventId}, or {@code unknown} when the value isn't JSON that has one. */
  private String eventIdOf(ConsumerRecord<?, ?> record) {
    if (record.value() instanceof String value) {
      try {
        var eventId = json.readTree(value).path("eventId");
        if (eventId.isString()) {
          return eventId.asString();
        }
      } catch (JacksonException e) {
        // Malformed JSON: the reason it was skipped, which the log line's exception shows.
      }
    }
    return "unknown";
  }
}
