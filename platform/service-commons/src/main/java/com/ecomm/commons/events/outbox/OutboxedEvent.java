package com.ecomm.commons.events.outbox;

import com.ecomm.commons.web.CorrelationId;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * An integration event as Spring Modulith's event publication registry stores it: the topic, the
 * key, the Correlation ID of the request that caused it, and the event's JSON as a map. Storing the
 * map rather than the service's own event record means a publication can be read back and
 * republished whatever class described it.
 *
 * @param correlationId {@code null} when the event wasn't caused by a request
 */
public record OutboxedEvent(
    String topic, String key, String correlationId, Map<String, Object> value) {

  /** The Kafka message: the value, with the Correlation ID as a header when there is one. */
  Message<Map<String, Object>> toMessage() {
    var message = MessageBuilder.withPayload(value);
    if (correlationId != null) {
      message.setHeader(CorrelationId.HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
    }
    return message.build();
  }
}
