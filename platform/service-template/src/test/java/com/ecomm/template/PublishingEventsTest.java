package com.ecomm.template;

import static com.ecomm.template.TestInfrastructure.GREETINGS;
import static com.ecomm.template.TestInfrastructure.LATE_GREETINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.events.IntegrationEventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishing an integration event through the port, in the transaction of the change it describes:
 * it reaches its topic only once that commits, matches its schema, and is retried until it does.
 */
@SpringBootTest(
    properties = {
      "ecomm.events.outbox.resubmit-failed-every=1s",
      // How long a send waits for a topic that doesn't exist before it fails.
      "spring.kafka.producer.properties[max.block.ms]=2000"
    })
@Import(Greetings.class)
class PublishingEventsTest {

  private static final JsonMapper JSON = new JsonMapper();

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired Greetings greetings;
  @Autowired IntegrationEventPublisher events;
  @Autowired JdbcTemplate jdbc;

  @Test
  void aCommittedChangeReachesItsTopicKeyedByItsAggregate() {
    var id = newGreetingId();

    greetings.save(id, "Hello");

    var record = Topics.recordKeyed(GREETINGS, id, Duration.ofSeconds(20)).orElseThrow();
    var event = JSON.readTree(record.value());
    assertThat(UUID.fromString(event.get("eventId").asString())).isNotNull();
    assertThat(Instant.parse(event.get("occurredAt").asString())).isNotNull();
    assertThat(event.get("greetingId").asString()).isEqualTo(id);
    assertThat(event.get("version").asLong()).isEqualTo(1);
    assertThat(event.get("change").asString()).isEqualTo("CREATED");
    assertThat(event.at("/greeting/text").asString()).isEqualTo("Hello");
  }

  @Test
  void anEventCarriesTheCorrelationIdOfTheRequestThatCausedIt() {
    var id = newGreetingId();

    try (var ignored = MDC.putCloseable("correlationId", "publish-corr-1")) {
      greetings.save(id, "Hello");
    }

    var record = Topics.recordKeyed(GREETINGS, id, Duration.ofSeconds(20)).orElseThrow();
    var header = record.headers().lastHeader("X-Correlation-Id");
    assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo("publish-corr-1");
  }

  @Test
  void aChangeThatRollsBackPublishesNothing() {
    var id = newGreetingId();

    assertThatThrownBy(() -> greetings.saveThenFail(id, "Hello"))
        .isInstanceOf(IllegalStateException.class);

    assertThat(greetings.exists(id)).isFalse();
    assertThat(publicationsOf(id)).isEmpty();
    assertThat(Topics.recordKeyed(GREETINGS, id, Duration.ofSeconds(3))).isEmpty();
  }

  @Test
  void publishingOutsideATransactionIsRefused() {
    var event =
        new Greetings.GreetingEvent(newGreetingId(), 1, "CREATED", new Greetings.Greeting("Hello"));

    assertThatThrownBy(() -> events.publish(event))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("inside the transaction");
  }

  @Test
  void anEventThatDoesntMatchItsSchemaIsRefusedAtProduce() {
    var id = newGreetingId();

    greetings.save(id, ""); // the schema requires at least one character

    await()
        .atMost(Duration.ofSeconds(20))
        .until(() -> publicationsOf(id).equals(List.of("FAILED")));
    assertThat(Topics.recordKeyed(GREETINGS, id, Duration.ofSeconds(3))).isEmpty();
  }

  @Test
  void aPublicationThatFailsIsRetriedAndStaysVisibleUntilItSucceeds() {
    var id = newGreetingId();

    greetings.saveToLateTopic(id, "Hello");

    await()
        .atMost(Duration.ofSeconds(20))
        .until(() -> publicationsOf(id).equals(List.of("FAILED")));
    EventBackbone.createTopic(LATE_GREETINGS);
    assertThat(Topics.recordKeyed(LATE_GREETINGS, id, Duration.ofSeconds(30))).isPresent();
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> publicationsOf(id).equals(List.of("COMPLETED")));
  }

  /** The status of each of the Greeting's rows in the outbox, the event publication registry. */
  private List<String> publicationsOf(String greetingId) {
    return jdbc.queryForList(
        "SELECT status FROM event_publication WHERE serialized_event LIKE ?",
        String.class,
        "%\"key\":\"" + greetingId + "\"%");
  }

  private static String newGreetingId() {
    return "greeting-" + UUID.randomUUID();
  }
}
