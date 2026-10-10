package com.ecomm.commons.events.kafka;

import com.ecomm.commons.web.CorrelationId;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames.HeadersToAdd;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.SingleRecordHeader;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The last resort for an event a listener couldn't handle: publishes it to its consumer group's
 * dead-letter topic (see {@link DeadLetterTopics}), logs its {@code eventId}, topic, partition and
 * offset at error level, counts it as {@code ecomm.events.dead-lettered} per group and topic, and
 * lets the partition move on.
 *
 * <p>The dead-letter record keeps the event's key, value and headers, the Correlation ID among
 * them, as they were read, with no schema. Spring Kafka's {@code kafka_dlt-*} headers add its
 * original topic, partition (a big-endian int) and offset (a big-endian long), the consumer group,
 * and the exception: its innermost cause's class, in both {@code -exception-fqcn} and {@code
 * -exception-cause-fqcn}, and that cause's message. {@value #DEAD_LETTERED_AT} adds when. A
 * redriven event that fails again keeps the original topic, partition and offset it was first
 * dead-lettered from.
 *
 * <p>If the dead-letter topic can't be written to, the event is retried again rather than lost.
 */
class DeadLetterRecoverer implements ConsumerAwareRecordRecoverer, DisposableBean {

  static final String DEAD_LETTERED_AT = "ecomm_dlt-dead-lettered-at";

  private static final Logger log = LoggerFactory.getLogger(DeadLetterRecoverer.class);

  private final DefaultKafkaProducerFactory<Object, Object> producers;
  private final DeadLetterPublishingRecoverer publisher;
  private final JsonMapper json;
  private final MeterRegistry registry;

  /**
   * Publishes with {@code producerFactory}'s configuration, but writes keys and values as the bytes
   * they were read as, rather than through a schema serializer.
   *
   * @param registry where the dead-lettered events are counted, or null not to count them
   */
  DeadLetterRecoverer(
      ProducerFactory<?, ?> producerFactory, JsonMapper json, MeterRegistry registry) {
    var asRead =
        new DelegatingByTypeSerializer(
            Map.of(byte[].class, new ByteArraySerializer(), String.class, new StringSerializer()));
    this.producers =
        new DefaultKafkaProducerFactory<>(
            producerFactory.getConfigurationProperties(), asRead, asRead);
    this.publisher =
        new DeadLetterPublishingRecoverer(
            new KafkaTemplate<>(producers),
            (record, failure) -> new TopicPartition(deadLetterTopic(), -1));
    publisher.excludeHeader(HeadersToAdd.EX_STACKTRACE);
    publisher.setAppendOriginalHeaders(false);
    publisher.addHeadersFunction(
        (record, failure) -> {
          var cause = rootCauseOf(failure);
          return new RecordHeaders()
              .add(header(KafkaHeaders.DLT_EXCEPTION_FQCN, cause.getClass().getName()))
              .add(header(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, cause.getClass().getName()))
              .add(header(KafkaHeaders.DLT_EXCEPTION_MESSAGE, String.valueOf(cause.getMessage())))
              .add(header(DEAD_LETTERED_AT, Instant.now().toString()));
        });
    this.json = json;
    this.registry = registry;
  }

  @Override
  public void accept(ConsumerRecord<?, ?> record, Consumer<?, ?> consumer, Exception failure) {
    publisher.accept(record, consumer, failure);
    try (var ignored =
        MDC.putCloseable(
            CorrelationId.MDC_KEY, CorrelationIdRecordInterceptor.correlationIdOf(record))) {
      log.error(
          "Dead-lettered event {} from {} partition {} offset {} to {}",
          eventIdOf(record),
          record.topic(),
          record.partition(),
          record.offset(),
          deadLetterTopic(),
          failure);
    }
    if (registry != null) {
      EventCounters.increment(
          registry,
          "ecomm.events.dead-lettered",
          "Events a listener couldn't handle, sent to its dead-letter topic",
          record);
    }
  }

  /**
   * The innermost cause, such as Jackson's exception naming a missing field, where Spring Kafka's
   * own headers would name its listener's and converter's wrappers.
   */
  private static Throwable rootCauseOf(Exception failure) {
    Throwable cause = failure;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause;
  }

  /** Replaces any header of the name, as one a redriven event brings would be. */
  private static SingleRecordHeader header(String name, String value) {
    return new SingleRecordHeader(name, value.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public void destroy() {
    producers.destroy();
  }

  private static String deadLetterTopic() {
    return DeadLetterTopics.deadLetterTopicOf(group());
  }

  /** The listener container sets it on the consumer thread the recoverer runs on. */
  private static String group() {
    return String.valueOf(KafkaUtils.getConsumerGroupId());
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
        // Malformed JSON: the reason it was dead-lettered, which the log line's exception shows.
      }
    }
    return "unknown";
  }
}
