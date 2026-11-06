package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;

import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

/**
 * Each Customer may send 20 messages an hour and 100 a day, whichever Conversations they are in.
 * Past either, a message is refused 429 {@code assistantRateLimited}, with {@code Retry-After}
 * saying when the Customer may ask again, before the provider is asked anything: the mock provider,
 * which answers here, is limited too. The clock stands still unless a test moves it on.
 */
@Import(SettableTimeSource.Clock.class)
class CustomerLimitsApiTest extends SupportAssistantApiTest {

  @Autowired SettableTimeSource time;

  @BeforeEach
  void resetClock() {
    time.reset();
  }

  /** Sends {@code count} messages, in Conversations of 20 at most, each answered. */
  private void sendAnswered(int count) {
    String id = null;
    for (var i = 0; i < count; i++) {
      if (i % 20 == 0) {
        id = started();
      }
      send(id, "Hello " + i).expectStatus().isOk();
    }
  }

  @Test
  void the21stMessageInAnHourIsRefusedUntilTheFirstIsAnHourOld() {
    sendAnswered(20);
    var id = started();
    stubLatestOrderPaid();

    send(id, "Where is my order?")
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .valueEquals("Retry-After", "3600")
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("assistantRateLimited");
    // The mock provider would have looked up the Customer's Orders.
    ORDER_MANAGEMENT.verify(0, anyRequestedFor(anyUrl()));
    transcript(customerToken(), id).expectBody().jsonPath("$.messages").isEmpty();

    time.advance(Duration.ofMinutes(59));
    send(id, "Hello").expectHeader().valueEquals("Retry-After", "60");
    time.advance(Duration.ofMinutes(1));
    send(id, "Hello").expectStatus().isOk();
  }

  @Test
  void the101stMessageInADayIsRefusedUntilTheFirstIsADayOld() {
    for (var hour = 0; hour < 5; hour++) {
      sendAnswered(20);
      time.advance(Duration.ofHours(1));
    }

    send(started(), "Hello")
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .valueEquals("Retry-After", String.valueOf(Duration.ofHours(19).toSeconds()))
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("assistantRateLimited");
    time.advance(Duration.ofHours(19));
    send(started(), "Hello").expectStatus().isOk();
  }

  @Test
  void anotherCustomerIsntLimitedByThisOnesMessages() throws Exception {
    sendAnswered(20);
    var other = FakeKeycloak.token("customer-7", "CUSTOMER");
    var id =
        start(other)
            .expectStatus()
            .isCreated()
            .expectBody(ConversationView.class)
            .returnResult()
            .getResponseBody()
            .id();

    send(other, id, "Hello").expectStatus().isOk();
  }

  @Test
  void aRefusedMessageDoesntCountAgainstTheLimit() {
    var id = started();
    for (var i = 0; i < 20; i++) {
      send(id, "").expectStatus().isBadRequest();
    }

    send(id, "Hello").expectStatus().isOk();
  }
}
