package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * A test stack shortens how long a Checkout Session holds the Cart ({@code
 * ecomm.checkout.session-lifetime}), so a test can wait one out: the session expires that long
 * after it starts, and its Reservation 2 minutes after that.
 */
@TestPropertySource(properties = "ecomm.checkout.session-lifetime=3m")
class SessionLifetimeApiTest extends CheckoutApiTest {

  @Test
  void aShortenedLifetimeHoldsTheCartThatLong() {
    stubSuccessfulCheckout();
    var before = Instant.now();

    var session = startedSession();

    assertThat(session.expiresAt())
        .isBetween(before.plus(Duration.ofMinutes(3)), Instant.now().plus(Duration.ofMinutes(3)));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/reservations"))
            .withRequestBody(
                matchingJsonPath(
                    "$.expiresAt",
                    equalTo(session.expiresAt().plus(Duration.ofMinutes(2)).toString()))));
  }

  @Test
  void aSessionPastTheShortenedLifetimeIsNoLongerCurrent() {
    stubSuccessfulCheckout();
    startedSession();

    clock.advance(Duration.ofMinutes(3));

    currentSession().expectStatus().isNotFound();
  }

  private SessionView startedSession() {
    return startSession()
        .expectStatus()
        .isCreated()
        .expectBody(SessionView.class)
        .returnResult()
        .getResponseBody();
  }
}
