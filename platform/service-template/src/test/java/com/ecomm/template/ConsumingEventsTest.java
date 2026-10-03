package com.ecomm.template;

import static com.ecomm.template.TestInfrastructure.GREETINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consuming integration events with what service-commons gives every listener: events applied by
 * version, a failing one retried then logged and skipped, and the Correlation ID in the logs. Logs
 * are ECS JSON lines, as under the docker profile. Events are sent as raw JSON, as any producer
 * might send them, including fields this consumer doesn't know.
 */
@SpringBootTest(
    properties = {
      "logging.structured.format.console=ecs",
      "ecomm.events.consumer.retries=2",
      "ecomm.events.consumer.initial-backoff=50ms"
    })
@Import(GreetingProjection.class)
@ExtendWith(OutputCaptureExtension.class)
class ConsumingEventsTest {

  private static final JsonMapper JSON = new JsonMapper();

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired GreetingProjection projection;

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
  void aMalformedEventIsLoggedAndSkippedWithoutStallingLaterOnes(CapturedOutput output) {
    var malformed = newGreetingId();
    var later = newGreetingId();

    Topics.send(GREETINGS, malformed, "{not json", null);
    send(later, 1, "Hello");

    awaitProjected(later, 1, "Hello");
    assertThat(logLineMentioning(output, "Skipped event unknown from " + GREETINGS))
        .contains("\"level\":\"ERROR\"");
  }

  @Test
  void anEventTheListenerKeepsFailingOnIsRetriedThenLoggedAndSkipped(CapturedOutput output) {
    var failing = newGreetingId();
    var later = newGreetingId();
    var eventId = UUID.randomUUID().toString();

    Topics.send(GREETINGS, failing, event(eventId, failing, 1, "boom"), null);
    send(later, 1, "Hello");

    awaitProjected(later, 1, "Hello");
    assertThat(projection.find(failing)).isEmpty();
    assertThat(projection.attemptsOn(failing)).isEqualTo(3); // the first, and 2 retries
    assertThat(logLineMentioning(output, "Skipped event " + eventId + " from " + GREETINGS))
        .contains("\"level\":\"ERROR\"")
        .containsPattern("partition 0 offset \\d+");
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

  private static String logLineMentioning(CapturedOutput output, String text) {
    return output.getOut().lines().filter(line -> line.contains(text)).findFirst().orElse("");
  }

  private static String newGreetingId() {
    return "greeting-" + UUID.randomUUID();
  }
}
