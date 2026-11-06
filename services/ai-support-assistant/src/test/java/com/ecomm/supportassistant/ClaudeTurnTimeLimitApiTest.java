package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * A turn's 60 seconds, shortened here to 2, bound Claude's calls too: a call is given only what is
 * left of them, not its own 30 seconds, and one cut off by them ends the turn with the reply saying
 * the question was too much to look up at once.
 */
@TestPropertySource(properties = "ecomm.support-assistant.turn-time-limit=2s")
class ClaudeTurnTimeLimitApiTest extends MessagesApiTest {

  @Test
  void aCallIsCutOffWhenTheTurnsTimeIsUp() {
    MESSAGES_API.stubFor(
        post(urlPathEqualTo("/v1/messages"))
            .willReturn(okJson(answer("end_turn", text("Hello!"))).withFixedDelay(5_000)));
    var id = started();
    var started = System.nanoTime();

    send(id, "Hello")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .value(String.class, text -> assertThat(text).contains("too much to look up at once"));

    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(4));
  }
}
