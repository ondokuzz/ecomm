package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * A provider that fails is answered fast as unavailable: 503 {@code assistantUnavailable}, with the
 * Conversation as it was. Its 429, 529, other 5xx and timeouts count against the circuit breaker,
 * which opens at 5 in a row, answers without asking the provider while open, and lets one call
 * through after its wait, 30 seconds, shortened here to 2; each call's timeout, 30 seconds, is
 * shortened to 1. A 400 or 401 is a bug or a bad key: logged with its error type, answered 503 the
 * same, and not counted.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(
    properties = {
      "ecomm.support-assistant.anthropic-timeout=1s",
      "ecomm.support-assistant.breaker.wait-when-open=2s",
      // So the breaker's whole window can be filled by one Customer.
      "ecomm.support-assistant.messages-per-hour=100"
    })
class ProviderFailuresApiTest extends MessagesApiTest {

  @Autowired CircuitBreaker breaker;

  @BeforeEach
  void closeBreaker() {
    breaker.reset();
  }

  /** The Messages API answers every call so, with no wait asked before the SDK's one retry. */
  static void failWith(ResponseDefinitionBuilder response) {
    MESSAGES_API.stubFor(
        post(urlPathEqualTo("/v1/messages"))
            .willReturn(response.withHeader("retry-after-ms", "1")));
  }

  static ResponseDefinitionBuilder error(int status, String type) {
    return aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withBody(
            """
            {"type": "error", "error": {"type": "%s", "message": "Something failed"}}
            """
                .formatted(type));
  }

  static final String REPLY = answer("end_turn", text("Hello! What can I look up for you?"));

  private void sendUnavailable(String id) {
    send(id, "Hello")
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("assistantUnavailable");
  }

  private int providerCalls() {
    return MESSAGES_API.findAll(postRequestedFor(urlPathEqualTo("/v1/messages"))).size();
  }

  @Test
  void aRateLimitOverloadServerErrorOrTimeoutIsAnsweredUnavailable() {
    var id = started();
    for (var failure :
        new ResponseDefinitionBuilder[] {
          error(429, "rate_limit_error"),
          error(529, "overloaded_error"),
          error(500, "api_error"),
          okJson(REPLY).withFixedDelay(1_500)
        }) {
      MESSAGES_API.resetAll();
      failWith(failure);

      sendUnavailable(id);
      // Retried once.
      assertThat(providerCalls()).isEqualTo(2);
    }
    transcript(customerToken(), id).expectBody().jsonPath("$.messages").isEmpty();
  }

  @Test
  void fiveFailuresInARowOpenTheBreakerWhichAnswersWithoutAskingThenLetsOneCallThrough()
      throws InterruptedException {
    failWith(error(529, "overloaded_error"));
    var id = started();
    for (var i = 0; i < 5; i++) {
      sendUnavailable(id);
    }
    var asked = providerCalls();

    sendUnavailable(id);
    assertThat(providerCalls()).isEqualTo(asked);

    Thread.sleep(Duration.ofMillis(2_100));
    MESSAGES_API.resetAll();
    MESSAGES_API.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(okJson(REPLY)));
    send(id, "Hello").expectStatus().isOk();
    send(id, "Hello").expectStatus().isOk();
    assertThat(providerCalls()).isEqualTo(2);
  }

  @Test
  void aCallLetThroughThatFailsOpensTheBreakerAgain() throws InterruptedException {
    failWith(error(500, "api_error"));
    var id = started();
    for (var i = 0; i < 5; i++) {
      sendUnavailable(id);
    }
    Thread.sleep(Duration.ofMillis(2_100));

    sendUnavailable(id);
    var asked = providerCalls();
    sendUnavailable(id);
    assertThat(providerCalls()).isEqualTo(asked);
  }

  @Test
  void halfOfTheLast20CallsFailingOpensTheBreaker() {
    var id = started();
    for (var i = 0; i < 10; i++) {
      MESSAGES_API.resetAll();
      MESSAGES_API.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(okJson(REPLY)));
      send(id, "Hello").expectStatus().isOk();
      MESSAGES_API.resetAll();
      failWith(error(529, "overloaded_error"));
      sendUnavailable(id);
    }
    MESSAGES_API.resetAll();
    MESSAGES_API.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(okJson(REPLY)));

    sendUnavailable(id);
    assertThat(providerCalls()).isZero();
  }

  @Test
  void aBadRequestOrBadKeyIsAnsweredUnavailableLoggedAndNotCounted(CapturedOutput output) {
    var id = started();
    for (var i = 0; i < 3; i++) {
      MESSAGES_API.resetAll();
      failWith(error(400, "invalid_request_error"));
      sendUnavailable(id);
      // Never retried.
      assertThat(providerCalls()).isEqualTo(1);
      MESSAGES_API.resetAll();
      failWith(error(401, "authentication_error"));
      sendUnavailable(id);
    }
    MESSAGES_API.resetAll();
    MESSAGES_API.stubFor(post(urlPathEqualTo("/v1/messages")).willReturn(okJson(REPLY)));

    send(id, "Hello").expectStatus().isOk();
    assertThat(output.getOut())
        .contains("invalid_request_error")
        .contains("authentication_error")
        .doesNotContain(API_KEY);
  }
}
