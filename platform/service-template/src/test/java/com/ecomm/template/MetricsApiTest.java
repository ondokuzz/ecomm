package com.ecomm.template;

import static com.ecomm.template.TestInfrastructure.GREETINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * What every service exports for Prometheus with no code of its own, at {@code
 * /actuator/prometheus} and without a token: HTTP request metrics, Kafka consumer metrics with
 * their lag per group, topic and partition, retried events, and the outbox's incomplete
 * publications. Every metric names the service.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "greetings.group-id=service-template.metrics",
      "ecomm.events.consumer.retries=1",
      "ecomm.events.consumer.initial-backoff=50ms"
    })
@AutoConfigureRestTestClient
@Import(GreetingProjection.class)
class MetricsApiTest {

  private static final JsonMapper JSON = new JsonMapper();
  private static final String SERVICE = "service=\"service-template\"";
  // A group of its own, so the Greetings aren't shared with another test's cached application.
  private static final String GROUP = "group=\"service-template.metrics\"";
  private static final String TOPIC = "topic=\"" + GREETINGS + "\"";

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    TestInfrastructure.registerWith(registry);
  }

  @Autowired RestTestClient http;
  @Autowired GreetingProjection projection;

  @Test
  void theHttpConsumerAndOutboxMetricsAreExposedWithTheServiceName() {
    http.get()
        .uri("/ping")
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("customer-42", "CUSTOMER")))
        .exchange()
        .expectStatus()
        .isOk();
    var failing = "greeting-" + UUID.randomUUID();
    Topics.send(GREETINGS, failing, boom(failing), null);
    await().atMost(Duration.ofSeconds(30)).until(() -> projection.attemptsOn(failing) == 2);

    // Per-partition consumer metrics show once Micrometer's minutely refresh finds them.
    await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () ->
                assertThat(scrape().lines())
                    .anySatisfy(
                        line ->
                            assertThat(line)
                                .startsWith("http_server_requests_seconds_bucket{")
                                .contains(SERVICE, "uri=\"/ping\"", "status=\"200\""))
                    .anySatisfy(
                        line ->
                            assertThat(line)
                                .startsWith("kafka_consumer_fetch_manager_records_lag{")
                                .contains(SERVICE, GROUP, TOPIC, "partition=\"0\""))
                    .anySatisfy(
                        line ->
                            assertThat(line)
                                .startsWith("kafka_consumer_fetch_manager_records_consumed_total{")
                                .contains(SERVICE, GROUP, TOPIC))
                    .anySatisfy(
                        line ->
                            assertThat(line)
                                .startsWith("ecomm_events_retried_total{")
                                .contains(SERVICE, GROUP, TOPIC))
                    .anySatisfy(
                        line ->
                            assertThat(line)
                                .startsWith("ecomm_outbox_incomplete_publications{")
                                .contains(SERVICE, "status=\"failed\"")));
  }

  private String scrape() {
    return http.get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  /** A Greeting the projection fails on every time. */
  private static String boom(String greetingId) {
    return JSON.writeValueAsString(
        Map.of(
            "eventId",
            UUID.randomUUID().toString(),
            "occurredAt",
            Instant.now().toString(),
            "greetingId",
            greetingId,
            "version",
            1,
            "change",
            "CREATED",
            "greeting",
            Map.of("text", "boom")));
  }
}
