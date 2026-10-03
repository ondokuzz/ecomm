package com.ecomm.commons.events.kafka;

import com.ecomm.commons.web.CorrelationId;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Puts an event's Correlation ID in the MDC while a listener handles it, so its log lines, and any
 * event or call it makes in turn, carry the ID of the request that caused the event. An event with
 * none, or a malformed one, gets a new ID, as a request does.
 */
class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

  @Override
  public ConsumerRecord<Object, Object> intercept(
      ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
    MDC.put(CorrelationId.MDC_KEY, correlationIdOf(record));
    return record;
  }

  @Override
  public void afterRecord(
      ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
    MDC.remove(CorrelationId.MDC_KEY);
  }

  static String correlationIdOf(ConsumerRecord<?, ?> record) {
    var header = record.headers().lastHeader(CorrelationId.HEADER);
    return CorrelationId.acceptOrGenerate(
        header == null ? null : new String(header.value(), StandardCharsets.UTF_8));
  }
}
