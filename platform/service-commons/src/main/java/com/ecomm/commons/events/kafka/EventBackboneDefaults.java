package com.ecomm.commons.events.kafka;

import java.util.LinkedHashMap;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ClassUtils;

/**
 * Defaults for every service on the event backbone, below anything a service configures itself.
 *
 * <p>With Kafka on the classpath, a new consumer group reads its topics from the start, so a
 * projection starts complete. With Apicurio's serializer on it too, the service is a producer:
 *
 * <ul>
 *   <li>each value is serialized by Apicurio's JSON Schema serializer, validated against the latest
 *       schema registered for its topic, and refused if it doesn't match. Producers never register
 *       schemas; {@code platform/event-schemas} does;
 *   <li>the schema's IDs travel in headers, so the value is plain JSON any consumer can read;
 *   <li>the registry is {@code ecomm.events.schema-registry-url}.
 * </ul>
 *
 * With Spring Modulith's Kafka externalization on it, the outbox:
 *
 * <ul>
 *   <li>Spring Modulith hands the event to the serializer as it is, rather than as JSON bytes, and
 *       republishes incomplete publications on restart.
 * </ul>
 */
class EventBackboneDefaults implements EnvironmentPostProcessor {

  private static final String KAFKA_CLIENTS = "org.apache.kafka.clients.producer.KafkaProducer";
  private static final String MODULITH_KAFKA =
      "org.springframework.modulith.events.kafka.KafkaEventExternalizerConfiguration";
  private static final String APICURIO_SERIALIZER =
      "io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    var classLoader = application.getClassLoader();
    if (!ClassUtils.isPresent(KAFKA_CLIENTS, classLoader)) {
      return;
    }
    var defaults = new LinkedHashMap<String, Object>();
    defaults.put("spring.kafka.consumer.auto-offset-reset", "earliest");
    if (ClassUtils.isPresent(APICURIO_SERIALIZER, classLoader)) {
      var apicurio = "spring.kafka.producer.properties[apicurio.registry.";
      defaults.put("spring.kafka.producer.value-serializer", APICURIO_SERIALIZER);
      defaults.put(apicurio + "url]", "${ecomm.events.schema-registry-url}");
      defaults.put(apicurio + "auto-register]", "false");
      defaults.put(apicurio + "find-latest]", "true");
      defaults.put(apicurio + "serde.validation-enabled]", "true");
      defaults.put(apicurio + "headers.enabled]", "true");
      defaults.put(apicurio + "http.adapter]", "JDK");
      defaults.put("ecomm.events.schema-registry-url", "http://localhost:8088/apis/registry/v3");
    }
    if (ClassUtils.isPresent(MODULITH_KAFKA, classLoader)) {
      defaults.put("spring.modulith.events.kafka.enable-json", "false");
      defaults.put("spring.modulith.events.republish-outstanding-events-on-restart", "true");
    }
    DefaultPropertiesPropertySource.addOrMerge(defaults, environment.getPropertySources());
  }
}
