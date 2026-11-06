package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * The service asks Claude at most 60 times a minute, across every Customer and every instance: past
 * that, a turn is answered 503 {@code assistantBusy}, with {@code Retry-After} saying when it may
 * be asked again. 60 is lowered here to 3. The clock stands still unless a test moves it on.
 */
@Import(SettableTimeSource.Clock.class)
@TestPropertySource(properties = "ecomm.support-assistant.provider-calls-per-minute=3")
class ProviderCeilingApiTest extends MessagesApiTest {

  @Autowired SettableTimeSource time;

  @BeforeEach
  void resetClock() {
    time.reset();
  }

  @Test
  void pastTheCeilingATurnIsBusyUntilTheFirstCallIsAMinuteOld() throws Exception {
    stubLatestOrderPaid();
    script(
        answer("tool_use", toolUse("toolu_01", "find_orders", "{}")),
        answer("end_turn", text("Your latest Order, #7F1C2A3B, is paid.")),
        answer("end_turn", text("Hello!")),
        answer("end_turn", text("Hello again!")));
    send(started(), "Where is my order?").expectStatus().isOk();
    time.advance(Duration.ofSeconds(20));
    var other = FakeKeycloak.token("customer-7", "CUSTOMER");
    var id =
        start(other)
            .expectStatus()
            .isCreated()
            .expectBody(ConversationView.class)
            .returnResult()
            .getResponseBody()
            .id();
    send(other, id, "Hi").expectStatus().isOk();

    send(other, id, "Hi")
        .expectStatus()
        .isEqualTo(503)
        .expectHeader()
        .valueEquals("Retry-After", "40")
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("assistantBusy");
    assertThat(MESSAGES_API.findAll(postRequestedFor(urlPathEqualTo("/v1/messages")))).hasSize(3);

    time.advance(Duration.ofSeconds(40));
    send(other, id, "Hi")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("Hello again!");
  }

  /** A retry is a call like any other. */
  @Test
  void aRetryCountsAgainstTheCeiling() {
    MESSAGES_API.stubFor(
        post(urlPathEqualTo("/v1/messages"))
            .willReturn(
                aResponse()
                    .withStatus(529)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("retry-after-ms", "1")
                    .withBody(
                        "{\"type\": \"error\", \"error\": {\"type\": \"overloaded_error\","
                            + " \"message\": \"Overloaded\"}}")));
    var id = started();
    // Asked, then retried once: 2 of the 3.
    send(id, "Hi").expectStatus().isEqualTo(503);

    // Asked once more, the ceiling's last; its retry would pass it.
    send(id, "Hi")
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("assistantBusy");
    assertThat(MESSAGES_API.findAll(postRequestedFor(urlPathEqualTo("/v1/messages")))).hasSize(3);
  }
}
