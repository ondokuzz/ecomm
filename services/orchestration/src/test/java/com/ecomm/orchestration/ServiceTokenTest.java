package com.ecomm.orchestration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.orchestration.adapter.out.http.ServiceRestClients;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * Orchestration calls other services with its own token, as Checkout does: fetched with its client
 * credentials, cached, replaced once it has less than a minute left, and replaced on a 401 before
 * one retry.
 */
@Import(ServiceTokenTest.Clocks.class)
class ServiceTokenTest extends OrchestrationTest {

  private static final String COMMAND = "/orders";

  @Autowired ServiceRestClients clients;
  @Autowired MovableClock clock;
  @Autowired OAuth2AuthorizedClientService authorizedClients;

  @BeforeEach
  void forgetTheToken() {
    authorizedClients.removeAuthorizedClient("orchestration", "orchestration");
    clock.reset();
  }

  @Test
  void theTokenIsFetchedWithOrchestrationsClientCredentials() {
    stubToken("orchestration-token-1", Duration.ofMinutes(5));
    stubCommand(ok());

    call();

    var clientIdAndSecret =
        Base64.getEncoder().encodeToString("orchestration:test".getBytes(UTF_8));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(TOKEN_PATH))
            .withHeader("Authorization", equalTo("Basic " + clientIdAndSecret))
            .withRequestBody(containing("grant_type=client_credentials")));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(COMMAND))
            .withHeader("Authorization", equalTo("Bearer orchestration-token-1")));
  }

  @Test
  void oneTokenServesEveryCall() {
    stubToken("orchestration-token-1", Duration.ofMinutes(5));
    stubCommand(ok());

    call();
    call();

    DOWNSTREAM.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
  }

  @Test
  void aTokenWithMoreThanAMinuteLeftIsReused() {
    stubToken("orchestration-token-1", Duration.ofMinutes(5));
    stubCommand(ok());
    call();

    clock.advance(Duration.ofMinutes(5).minusSeconds(70));
    call();

    DOWNSTREAM.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
  }

  @Test
  void aTokenWithLessThanAMinuteLeftIsReplacedBeforeTheNextCall() {
    stubTokenSequence("orchestration-token-1", "orchestration-token-2");
    stubCommand(ok());
    call();

    clock.advance(Duration.ofMinutes(5).minusSeconds(50));
    call();

    DOWNSTREAM.verify(2, postRequestedFor(urlEqualTo(TOKEN_PATH)));
    DOWNSTREAM.verify(
        1,
        postRequestedFor(urlEqualTo(COMMAND))
            .withHeader("Authorization", equalTo("Bearer orchestration-token-1")));
    DOWNSTREAM.verify(
        1,
        postRequestedFor(urlEqualTo(COMMAND))
            .withHeader("Authorization", equalTo("Bearer orchestration-token-2")));
  }

  @Test
  void a401IsRetriedOnceWithAFreshToken() {
    stubTokenSequence("revoked-token", "orchestration-token-2");
    DOWNSTREAM.stubFor(
        post(COMMAND)
            .withHeader("Authorization", equalTo("Bearer revoked-token"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"")));
    DOWNSTREAM.stubFor(
        post(COMMAND)
            .withHeader("Authorization", equalTo("Bearer orchestration-token-2"))
            .willReturn(ok("done")));

    assertThat(call()).isEqualTo("done");

    DOWNSTREAM.verify(2, postRequestedFor(urlEqualTo(COMMAND)));
    DOWNSTREAM.verify(2, postRequestedFor(urlEqualTo(TOKEN_PATH)));
  }

  @Test
  void a401IsRetriedOnlyOnce() {
    stubToken("orchestration-token-1", Duration.ofMinutes(5));
    stubCommand(aResponse().withStatus(401));

    assertThatThrownBy(this::call).isInstanceOf(HttpClientErrorException.Unauthorized.class);

    DOWNSTREAM.verify(2, postRequestedFor(urlEqualTo(COMMAND)));
  }

  @Test
  void noOtherStatusIsRetried() {
    stubToken("orchestration-token-1", Duration.ofMinutes(5));
    stubCommand(aResponse().withStatus(503));

    assertThatThrownBy(this::call).isInstanceOf(HttpServerErrorException.class);

    DOWNSTREAM.verify(1, postRequestedFor(urlEqualTo(COMMAND)));
  }

  @Test
  void a403KeepsTheCachedTokenAndIsNotRetried() {
    stubToken("orchestration-token-1", Duration.ofMinutes(5));
    stubCommand(
        aResponse()
            .withStatus(403)
            .withHeader("WWW-Authenticate", "Bearer error=\"insufficient_scope\""));
    assertThatThrownBy(this::call).isInstanceOf(HttpClientErrorException.Forbidden.class);

    stubCommand(ok());
    call();

    DOWNSTREAM.verify(2, postRequestedFor(urlEqualTo(COMMAND)));
    DOWNSTREAM.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
  }

  private String call() {
    RestClient http = clients.forService(DOWNSTREAM.baseUrl());
    return http.post().uri(COMMAND).retrieve().body(String.class);
  }

  private static void stubCommand(ResponseDefinitionBuilder response) {
    DOWNSTREAM.stubFor(post(COMMAND).willReturn(response));
  }

  private static void stubToken(String token, Duration expiresIn) {
    DOWNSTREAM.stubFor(post(TOKEN_PATH).willReturn(tokenResponse(token, expiresIn)));
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

  /** A clock the tests move forward, to bring the cached token near its expiry. */
  @TestConfiguration
  static class Clocks {

    @Bean
    @Primary
    MovableClock movableClock() {
      return new MovableClock();
    }
  }

  /** A clock that reads the real time plus however far a test has moved it. */
  static final class MovableClock extends Clock {

    private volatile Duration offset = Duration.ZERO;

    void advance(Duration by) {
      offset = offset.plus(by);
    }

    void reset() {
      offset = Duration.ZERO;
    }

    @Override
    public Instant instant() {
      return Instant.now().plus(offset);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }
  }
}
