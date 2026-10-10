package com.ecomm.template;

import static com.ecomm.template.TestInfrastructure.GREETINGS;
import static com.ecomm.template.TestInfrastructure.GREETINGS_GROUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.events.kafka.DeadLetterRedrive;
import com.ecomm.commons.events.kafka.DeadLetterTopics;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consuming integration events with what service-commons gives every listener: events applied by
 * version, a failing one retried then sent to the consumer group's dead-letter topic, one that
 * can't be read sent there at once, dead events redriven, and the Correlation ID in the logs. Logs
 * are ECS JSON lines, as under the docker profile. Events are sent as raw JSON, as any producer
 * might send them, including fields this consumer doesn't know.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "logging.structured.format.console=ecs",
      "ecomm.events.consumer.retries=2",
      "ecomm.events.consumer.initial-backoff=50ms"
    })
@AutoConfigureRestTestClient
@Import(GreetingProjection.class)
@ExtendWith(OutputCaptureExtension.class)
class ConsumingEventsTest {

  private static final JsonMapper JSON = new JsonMapper();

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired GreetingProjection projection;
  @Autowired RestTestClient http;

  @Test
  void aNewerVersionIsApplied() {
    var id = newGreetingId();

    send(id, 1, "Hello");
    send(id, 2, "Hello again");

    awaitProjected(id, 2, "Hello again");
  }

  @Test
  void aDuplicateAndAnOlderVersionAreIgnored() {
    var id = newGreetingId();
    send(id, 2, "Hello");
    awaitProjected(id, 2, "Hello");

    send(id, 2, "Duplicate");
    send(id, 1, "Older");
    awaitAllHandled();

    assertThat(projection.find(id)).contains(new GreetingProjection.Projected(2, "Hello"));
  }

  @Test
  void aMalformedEventIsDeadLetteredAtOnceWithoutStallingLaterOnes(CapturedOutput output) {
    var malformed = newGreetingId();
    var later = newGreetingId();
    var retriesBefore = retries();

    Topics.send(GREETINGS, malformed, "{not json", null);
    send(later, 1, "Hello");

    awaitProjected(later, 1, "Hello");
    assertThat(deadLetterKeyed(malformed).value()).isEqualTo("{not json");
    assertThat(retries()).isEqualTo(retriesBefore);
    assertThat(logLineMentioning(output, "Dead-lettered event unknown from " + GREETINGS))
        .contains("\"level\":\"ERROR\"");
  }

  @Test
  void anEventMissingARequiredFieldIsDeadLetteredAtOnce() {
    var missing = newGreetingId();
    var later = newGreetingId();
    var retriesBefore = retries();
    var withoutGreeting =
        JSON.writeValueAsString(
            Map.of("eventId", UUID.randomUUID().toString(), "greetingId", missing, "version", 1));

    Topics.send(GREETINGS, missing, withoutGreeting, null);
    send(later, 1, "Hello");

    awaitProjected(later, 1, "Hello");
    var dead = deadLetterKeyed(missing);
    assertThat(dead.value()).isEqualTo(withoutGreeting);
    assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).endsWith("MismatchedInputException");
    assertThat(header(dead, "kafka_dlt-exception-message"))
        .contains("Missing required creator property 'greeting'");
    assertThat(retries()).isEqualTo(retriesBefore);
  }

  @Test
  void anEventTheListenerKeepsFailingOnIsRetriedThenDeadLettered(CapturedOutput output) {
    var failing = newGreetingId();
    var later = newGreetingId();
    var eventId = UUID.randomUUID().toString();
    var value = event(eventId, failing, 1, "boom");

    Topics.send(GREETINGS, failing, value, "dead-1");
    send(later, 1, "Hello");

    awaitProjected(later, 1, "Hello");
    assertThat(projection.find(failing)).isEmpty();
    assertThat(projection.attemptsOn(failing)).isEqualTo(3); // the first, and 2 retries
    var dead = deadLetterKeyed(failing);
    assertThat(dead.value()).isEqualTo(value);
    assertThat(header(dead, "X-Correlation-Id")).isEqualTo("dead-1");
    assertThat(header(dead, "kafka_dlt-original-topic")).isEqualTo(GREETINGS);
    assertThat(intHeader(dead, "kafka_dlt-original-partition")).isZero();
    assertThat(longHeader(dead, "kafka_dlt-original-offset")).isNotNegative();
    assertThat(header(dead, "kafka_dlt-original-consumer-group")).isEqualTo(GREETINGS_GROUP);
    assertThat(header(dead, "kafka_dlt-exception-fqcn"))
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(header(dead, "kafka_dlt-exception-cause-fqcn"))
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(header(dead, "kafka_dlt-exception-message")).contains("Cannot apply this Greeting");
    assertThat(Instant.parse(header(dead, "ecomm_dlt-dead-lettered-at")))
        .isBetween(Instant.now().minusSeconds(60), Instant.now());
    assertThat(logLineMentioning(output, "Dead-lettered event " + eventId + " from " + GREETINGS))
        .contains("\"level\":\"ERROR\"")
        .containsPattern(
            "partition 0 offset \\d+ to " + DeadLetterTopics.deadLetterTopicOf(GREETINGS_GROUP));
  }

  @Test
  void aRedrivenEventIsAppliedOnceItsCauseIsFixed() {
    var id = newGreetingId();
    send(id, 1, "boom");
    awaitDeadLetters(id, 1);
    var redrivenBefore = redriven();

    projection.fix(id);
    DeadLetterRedrive.redrive(EventBackbone.bootstrapServers(), GREETINGS_GROUP);

    awaitProjected(id, 1, "boom");
    await().atMost(Duration.ofSeconds(5)).until(() -> redriven() == redrivenBefore + 1);
  }

  @Test
  void aStaleRedrivenEventIsIgnored(CapturedOutput output) {
    var id = newGreetingId();
    send(id, 1, "boom");
    send(id, 2, "Hello");
    awaitProjected(id, 2, "Hello");
    awaitDeadLetters(id, 1);

    projection.fix(id);
    DeadLetterRedrive.redrive(EventBackbone.bootstrapServers(), GREETINGS_GROUP);

    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> !logLineMentioning(output, "Ignored greeting " + id + " version 1").isEmpty());
    assertThat(projection.find(id)).contains(new GreetingProjection.Projected(2, "Hello"));
  }

  @Test
  void aRedrivenEventThatFailsAgainIsDeadLetteredAgain() {
    var id = newGreetingId();
    send(id, 1, "boom");
    awaitDeadLetters(id, 1);

    DeadLetterRedrive.redrive(EventBackbone.bootstrapServers(), GREETINGS_GROUP);

    var again = awaitDeadLetters(id, 2).getLast();
    assertThat(header(again, "kafka_dlt-original-topic")).isEqualTo(GREETINGS);
    assertThat(projection.find(id)).isEmpty();
  }

  @Test
  void theCorrelationIdReachesTheConsumersLogLines(CapturedOutput output) {
    var id = newGreetingId();

    Topics.send(GREETINGS, id, event(UUID.randomUUID().toString(), id, 1, "Hello"), "consume-1");

    awaitProjected(id, 1, "Hello");
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(logLineMentioning(output, "Applied greeting " + id))
                    .contains("\"correlationId\":\"consume-1\""));
  }

  /**
   * A Greeting event with a field this consumer doesn't know, as a later version of the schema
   * might add.
   */
  private static String event(String eventId, String greetingId, long version, String text) {
    return JSON.writeValueAsString(
        Map.of(
            "eventId",
            eventId,
            "occurredAt",
            Instant.now().toString(),
            "greetingId",
            greetingId,
            "version",
            version,
            "change",
            version == 1 ? "CREATED" : "CHANGED",
            "greeting",
            Map.of("text", text, "addedLater", true)));
  }

  private static void send(String greetingId, long version, String text) {
    Topics.send(
        GREETINGS,
        greetingId,
        event(UUID.randomUUID().toString(), greetingId, version, text),
        null);
  }

  private void awaitProjected(String greetingId, long version, String text) {
    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () ->
                projection
                    .find(greetingId)
                    .equals(Optional.of(new GreetingProjection.Projected(version, text))));
  }

  /** Events on one partition are handled in order, so a later one applied means all are handled. */
  private void awaitAllHandled() {
    var marker = newGreetingId();
    send(marker, 1, "Marker");
    awaitProjected(marker, 1, "Marker");
  }

  /** Events on one partition are handled in order, so read once a later one is applied. */
  private double retries() {
    return scraped("ecomm_events_retried_total");
  }

  private double redriven() {
    return scraped("ecomm_events_redriven_total");
  }

  /** The Greetings' count of {@code counter} at {@code /actuator/prometheus}, 0 before any. */
  private double scraped(String counter) {
    var scrape =
        http.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    return scrape
        .lines()
        .filter(line -> line.startsWith(counter + "{"))
        .filter(line -> line.contains("group=\"" + GREETINGS_GROUP + "\""))
        .filter(line -> line.contains("topic=\"" + GREETINGS + "\""))
        .mapToDouble(line -> Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
        .sum();
  }

  private static List<ConsumerRecord<String, String>> awaitDeadLetters(String key, int count) {
    var deadLetters = new AtomicReference<List<ConsumerRecord<String, String>>>();
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> deadLetters.updateAndGet(any -> recordsKeyed(key)).size() == count);
    return deadLetters.get();
  }

  private static List<ConsumerRecord<String, String>> recordsKeyed(String key) {
    return EventBackbone.recordsKeyed(DeadLetterTopics.deadLetterTopicOf(GREETINGS_GROUP), key);
  }

  private static ConsumerRecord<String, String> deadLetterKeyed(String key) {
    return Topics.recordKeyed(
            DeadLetterTopics.deadLetterTopicOf(GREETINGS_GROUP), key, Duration.ofSeconds(10))
        .orElseThrow(() -> new AssertionError("Nothing keyed " + key + " was dead-lettered"));
  }

  private static String header(ConsumerRecord<?, ?> record, String name) {
    var header = record.headers().lastHeader(name);
    assertThat(header).as(name).isNotNull();
    return new String(header.value(), StandardCharsets.UTF_8);
  }

  /** Spring Kafka writes the original partition as a big-endian int. */
  private static int intHeader(ConsumerRecord<?, ?> record, String name) {
    return ByteBuffer.wrap(record.headers().lastHeader(name).value()).getInt();
  }

  /** And the original offset as a big-endian long. */
  private static long longHeader(ConsumerRecord<?, ?> record, String name) {
    return ByteBuffer.wrap(record.headers().lastHeader(name).value()).getLong();
  }

  private static String logLineMentioning(CapturedOutput output, String text) {
    return output.getOut().lines().filter(line -> line.contains(text)).findFirst().orElse("");
  }

  private static String newGreetingId() {
    return "greeting-" + UUID.randomUUID();
  }
}
