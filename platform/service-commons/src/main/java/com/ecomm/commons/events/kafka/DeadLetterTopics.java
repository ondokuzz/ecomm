package com.ecomm.commons.events.kafka;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.KafkaHeaders;

/**
 * Each consumer group's dead-letter topic, {@code <group>.dlt}, where an event it couldn't handle
 * goes, and its redrive topic, {@code <group>.redrive}, which its listeners also consume and where
 * {@link DeadLetterRedrive} copies dead events once their cause is fixed.
 */
public final class DeadLetterTopics {

  private static final String DEAD_LETTER = ".dlt";
  private static final String REDRIVE = ".redrive";

  private DeadLetterTopics() {}

  public static String deadLetterTopicOf(String group) {
    return group + DEAD_LETTER;
  }

  public static String redriveTopicOf(String group) {
    return group + REDRIVE;
  }

  static boolean isRedrive(ConsumerRecord<?, ?> record) {
    return record.topic().endsWith(REDRIVE);
  }

  /**
   * The topic the event was first consumed from: its own, or, for a redriven event, the one its
   * dead-letter headers name.
   */
  static String originalTopicOf(ConsumerRecord<?, ?> record) {
    var original = record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC);
    return isRedrive(record) && original != null
        ? new String(original.value(), StandardCharsets.UTF_8)
        : record.topic();
  }
}
