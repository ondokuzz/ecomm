package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Checkout's own token is cached, replaced once it has less than a minute left, and replaced on a
 * 401 before one retry. Keycloak being down when a Checkout Session starts is a 503.
 */
class ServiceTokenApiTest extends CheckoutApiTest {

  @Test
  void oneTokenServesEveryCallAcrossCheckouts() {
    stubSuccessfulCheckout();

    startedSessionId();
    startedSessionId();

    DOWNSTREAM.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
  }

  @Test
  void aTokenWithMoreThanAMinuteLeftIsReused() {
    stubSuccessfulCheckout();
    startedSessionId();

    clock.advance(Duration.ofMinutes(5).minusSeconds(70));
    startedSessionId();

    DOWNSTREAM.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
  }

  @Test
  void aTokenWithLessThanAMinuteLeftIsReplacedBeforeTheNextCall() {
    stubSuccessfulCheckout();
    stubTokenSequence("checkout-token-1", "checkout-token-2");
    startedSessionId();

    clock.advance(Duration.ofMinutes(5).minusSeconds(50));
    startedSessionId();

    DOWNSTREAM.verify(2, postRequestedFor(urlEqualTo(TOKEN_PATH)));
    DOWNSTREAM.verify(
        1,
        postRequestedFor(urlEqualTo("/reservations"))
            .withHeader("Authorization", equalTo("Bearer checkout-token-1")));
    DOWNSTREAM.verify(
        1,
        postRequestedFor(urlEqualTo("/reservations"))
            .withHeader("Authorization", equalTo("Bearer checkout-token-2")));
  }

  @Test
  void a401IsRetriedOnceWithAFreshToken() {
    stubSuccessfulCheckout();
    stubTokenSequence("revoked-token", "checkout-token-2");
    DOWNSTREAM.stubFor(
        post("/reservations")
            .withHeader("Authorization", equalTo("Bearer revoked-token"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"")));

    startSession().expectStatus().isCreated();

    DOWNSTREAM.verify(2, postRequestedFor(urlEqualTo("/reservations")));
    DOWNSTREAM.verify(2, postRequestedFor(urlEqualTo(TOKEN_PATH)));
  }

  @Test
  void a401IsRetriedOnlyOnce() {
    stubSuccessfulCheckout();
    stubReserve(aResponse().withStatus(401));

    startSession().expectStatus().isEqualTo(502);

    DOWNSTREAM.verify(2, postRequestedFor(urlEqualTo("/reservations")));
  }

  @Test
  void noOtherStatusIsRetried() {
    stubSuccessfulCheckout();
    stubReserve(aResponse().withStatus(503));

    startSession().expectStatus().isEqualTo(502);

    DOWNSTREAM.verify(1, postRequestedFor(urlEqualTo("/reservations")));
  }

  @Test
  void a403KeepsTheCachedTokenAndIsNotRetried() {
    stubSuccessfulCheckout();
    stubReserve(
        aResponse()
            .withStatus(403)
            .withHeader("WWW-Authenticate", "Bearer error=\"insufficient_scope\""));
    startSession().expectStatus().isEqualTo(502);

    stubReserve();
    startSession().expectStatus().isCreated();

    DOWNSTREAM.verify(2, postRequestedFor(urlEqualTo("/reservations")));
    DOWNSTREAM.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
  }

  @Test
  void keycloakBeingDownWhenASessionStartsIsA503() {
    stubSuccessfulCheckout();
    DOWNSTREAM.stubFor(
        post(TOKEN_PATH).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    var problem =
        startSession()
            .expectStatus()
            .isEqualTo(503)
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    assertThat(problem).contains("\"status\":503");
    currentSession().expectStatus().isNotFound();
  }

  /**
   * Keycloak hands out {@code first}, then {@code second} from the next request on. Token lifetimes
   * are stamped from the real clock, not the test's moved one, so {@code second} lasts an hour: it
   * must still look fresh to a clock a test has moved minutes ahead.
   */
  private static void stubTokenSequence(String first, String second) {
    DOWNSTREAM.stubFor(
        post(TOKEN_PATH)
            .inScenario("tokens")
            .whenScenarioStateIs(STARTED)
            .willReturn(tokenResponse(first, Duration.ofMinutes(5)))
            .willSetStateTo("second"));
    DOWNSTREAM.stubFor(
        post(TOKEN_PATH)
            .inScenario("tokens")
            .whenScenarioStateIs("second")
            .willReturn(tokenResponse(second, Duration.ofHours(1))));
  }

  private static ResponseDefinitionBuilder tokenResponse(String token, Duration expiresIn) {
    return okJson(
        """
        {"access_token": "%s", "token_type": "Bearer", "expires_in": %d}
        """
            .formatted(token, expiresIn.toSeconds()));
  }
}
