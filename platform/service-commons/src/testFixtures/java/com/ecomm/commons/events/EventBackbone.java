package com.ecomm.commons.events;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka and an Apicurio registry for tests, one of each per test JVM. As in the compose stack, the
 * broker creates no topics and producers find schemas only once they are registered; unlike it,
 * each topic has one partition, so a test sees every event in the order it was sent. Point the
 * service at them with:
 *
 * <pre>{@code
 * @DynamicPropertySource
 * static void eventBackbone(DynamicPropertyRegistry registry) {
 *   EventBackbone.registerWith(registry);
 * }
 * }</pre>
 *
 * and create each topic the test uses, with its schema, before the application starts: {@code
 * EventBackbone.createTopic("inventory.stock")} and {@code
 * EventBackbone.registerSchemaOf("inventory.stock")}.
 */
public final class EventBackbone {

  private static final KafkaContainer KAFKA =
      new KafkaContainer("apache/kafka:4.3.1").withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
  private static final GenericContainer<?> REGISTRY =
      new GenericContainer<>("apicurio/apicurio-registry:3.3.3")
          .withExposedPorts(8080)
          .waitingFor(Wait.forHttp("/apis/registry/v3/system/info"));
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final JsonMapper JSON = new JsonMapper();

  static {
    Startables.deepStart(KAFKA, REGISTRY).join();
  }

  private EventBackbone() {}

  public static void registerWith(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", EventBackbone::bootstrapServers);
    registry.add("ecomm.events.schema-registry-url", EventBackbone::registryUrl);
  }

  public static String bootstrapServers() {
    return KAFKA.getBootstrapServers();
  }

  /** The registry's v3 API. */
  public static String registryUrl() {
    return "http://%s:%d/apis/registry/v3"
        .formatted(REGISTRY.getHost(), REGISTRY.getMappedPort(8080));
  }

  /** Creates a compacted topic with one partition, if it doesn't exist yet. */
  public static void createTopic(String topic) {
    try (var admin = Admin.create(Map.of("bootstrap.servers", bootstrapServers()))) {
      admin
          .createTopics(
              List.of(
                  new NewTopic(topic, 1, (short) 1).configs(Map.of("cleanup.policy", "compact"))))
          .all()
          .get();
    } catch (ExecutionException e) {
      if (!(e.getCause() instanceof TopicExistsException)) {
        throw new IllegalStateException("Could not create " + topic, e.getCause());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** Registers the topic's schema from {@code platform/event-schemas}, as the stack does. */
  public static void registerSchemaOf(String topic) {
    var resource = "event-schemas/" + topic + ".json";
    try (var in = EventBackbone.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalArgumentException(
            "No " + resource + " on the classpath; add platform:event-schemas to the test deps");
      }
      registerSchema(topic, new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Registers {@code schema} as the latest version of the topic's value schema, with BACKWARD
   * compatibility as {@code register.sh} sets it. Registering the same schema again changes
   * nothing.
   */
  private static void registerSchema(String topic, String schema) {
    var artifacts = registryUrl() + "/groups/default/artifacts";
    var artifact = topic + "-value";
    var body =
        Map.of(
            "artifactId",
            artifact,
            "artifactType",
            "JSON",
            "firstVersion",
            Map.of("content", Map.of("content", schema, "contentType", "application/json")));
    var rules = artifacts + "/" + artifact + "/rules";
    // An existing artifact gets its rule first, so a changed schema is checked against it.
    var ruleAdded = requireBackwardCompatibility(rules);
    var created =
        post(artifacts + "?ifExists=FIND_OR_CREATE_VERSION", JSON.writeValueAsString(body));
    if (created.statusCode() != 200) {
      throw new IllegalStateException("Registering " + artifact + " failed: " + created.body());
    }
    if (!ruleAdded) {
      requireBackwardCompatibility(rules);
    }
  }

  /** Adds the BACKWARD rule at {@code rules}; false if there is no artifact to add it to yet. */
  private static boolean requireBackwardCompatibility(String rules) {
    var rule = post(rules, "{\"ruleType\":\"COMPATIBILITY\",\"config\":\"BACKWARD\"}");
    return switch (rule.statusCode()) {
      case 204, 409 -> true;
      case 404 -> false;
      default -> throw new IllegalStateException("Adding " + rules + " failed: " + rule.body());
    };
  }

  private static HttpResponse<String> post(String uri, String body) {
    try {
      return HTTP.send(
          HttpRequest.newBuilder(URI.create(uri))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
