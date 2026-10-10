package com.ecomm.commons.events.kafka;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.kafka.annotation.KafkaListenerAnnotationBeanPostProcessor.AnnotationEnhancer;

/**
 * Adds its consumer group's redrive topic (see {@link DeadLetterTopics}) to every
 * {@code @KafkaListener} that names its topics and its group, so the listener handles a redriven
 * event as it would the original. The group may be a placeholder, which is resolved with the topic.
 */
class RedriveTopicEnhancer implements AnnotationEnhancer {

  @Override
  public Map<String, Object> apply(Map<String, Object> attributes, AnnotatedElement element) {
    var topics = (String[]) attributes.get("topics");
    var group = (String) attributes.get("groupId");
    if (topics == null || topics.length == 0 || group == null || group.isEmpty()) {
      return attributes;
    }
    var enhanced = new LinkedHashMap<>(attributes);
    enhanced.put(
        "topics",
        Stream.concat(Arrays.stream(topics), Stream.of(DeadLetterTopics.redriveTopicOf(group)))
            .toArray(String[]::new));
    return enhanced;
  }
}
