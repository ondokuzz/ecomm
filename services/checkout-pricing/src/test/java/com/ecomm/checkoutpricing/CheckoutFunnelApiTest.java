package com.ecomm.checkoutpricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.checkoutpricing.domain.PaymentAttempt.Status;
import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * {@code checkout_sessions_total{step}} counts Checkout Sessions, each once per step it reaches,
 * however often the Customer repeats it or reads how it went. Every step is reported at 0 from the
 * start, so that Grafana's {@code increase()} counts the first. The context is fresh, so nothing
 * has been counted before the first test; later tests read how far each counter moved.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CheckoutFunnelApiTest extends CheckoutApiTest {

  static final List<String> STEPS =
      List.of(
          "started",
          "addressed",
          "optionChosen",
          "payAttempted",
          "paid",
          "declined",
          "holdExpired",
          "expired");

  @Test
  @Order(1)
  void everyStepIsReportedAtZeroBeforeAnySession() {
    assertThat(funnel()).containsExactlyInAnyOrderEntriesOf(moved());
  }

  @Test
  void aPaidSessionCountsEachStepOnceHoweverOftenItIsRepeated() {
    stubSuccessfulCheckout();
    var before = funnel();

    var sessionId = startedSessionId();
    ship(sessionId);
    ship(sessionId);
    pay(sessionId).expectStatus().isOk();
    pay(sessionId).expectStatus().isOk();
    payment(sessionId).expectStatus().isOk();

    assertThat(movedSince(before))
        .isEqualTo(moved("started", "addressed", "optionChosen", "payAttempted", "paid"));
  }

  @Test
  void aSessionDeclinedThenPaidCountsBoth() {
    stubSuccessfulCheckout();
    saga.answer(Status.DECLINED, "card_declined");
    var sessionId = shippedSessionId();
    var before = funnel();

    payWithMethod(sessionId, "tok_decline").expectStatus().isEqualTo(402);
    payWithMethod(sessionId, "tok_decline").expectStatus().isEqualTo(402);
    saga.answer(Status.PAID);
    pay(sessionId).expectStatus().isOk();

    assertThat(movedSince(before)).isEqualTo(moved("payAttempted", "declined", "paid"));
  }

  @Test
  void aHoldThatRanOutIsCounted() {
    stubSuccessfulCheckout();
    saga.answer(Status.HOLD_EXPIRED);
    var sessionId = shippedSessionId();
    var before = funnel();

    pay(sessionId).expectStatus().isEqualTo(410);

    assertThat(movedSince(before)).isEqualTo(moved("payAttempted", "holdExpired"));
  }

  @Test
  void aPaymentStillRunningIsCountedWhenItsOutcomeIsRead() {
    stubSuccessfulCheckout();
    saga.answer(Status.PROCESSING);
    var sessionId = shippedSessionId();
    var before = funnel();

    pay(sessionId).expectStatus().isAccepted();
    payment(sessionId).expectStatus().isOk();
    assertThat(movedSince(before)).isEqualTo(moved("payAttempted"));

    saga.finish(sessionId, Status.PAID);
    payment(sessionId).expectStatus().isOk();
    payment(sessionId).expectStatus().isOk();

    assertThat(movedSince(before)).isEqualTo(moved("payAttempted", "paid"));
  }

  @Test
  void aFailedSagaCountsOnlyTheAttempt() {
    stubSuccessfulCheckout();
    saga.answer(Status.FAILED);
    var sessionId = shippedSessionId();
    var before = funnel();

    pay(sessionId).expectStatus().isEqualTo(502);

    assertThat(movedSince(before)).isEqualTo(moved("payAttempted"));
  }

  @Test
  void aSessionFoundExpiredIsCountedOnceAndNotAgainWhenRedisDropsIt() throws Exception {
    stubSuccessfulCheckout();
    var sessionId = shippedSessionId();
    var before = funnel();

    clock.advance(Duration.ofMinutes(16));
    currentSession().expectStatus().isNotFound();
    pay(sessionId).expectStatus().isEqualTo(410);
    // Started afresh, replacing it.
    startedSessionId();
    assertThat(movedSince(before)).isEqualTo(moved("expired", "started"));

    var other = shippedSessionId();
    clock.advance(Duration.ofMinutes(16));
    pay(other).expectStatus().isEqualTo(410);
    dropFromRedis(other);

    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(movedSince(before))
                    .isEqualTo(
                        moved(
                            "expired",
                            "expired",
                            "started",
                            "started",
                            "addressed",
                            "optionChosen")));
  }

  @Test
  void aSessionRedisDropsUnreadIsCountedExpired() throws Exception {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    var before = funnel();

    dropFromRedis(sessionId);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(movedSince(before)).isEqualTo(moved("expired")));
  }

  @Test
  void anEndedOrReplacedSessionIsNotCountedExpired() throws Exception {
    stubSuccessfulCheckout();
    var before = funnel();

    startedSessionId();
    startedSessionId();

    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(movedSince(before)).isEqualTo(moved("started", "started")));
  }

  @Test
  void aPaymentNobodyReadsIsCountedPaidWhenTheSagaEndsTheSession() {
    stubSuccessfulCheckout();
    saga.answer(Status.PROCESSING);
    var sessionId = shippedSessionId();
    var before = funnel();
    pay(sessionId).expectStatus().isAccepted();

    saga.finish(sessionId, Status.PAID);
    endAsOrchestration(sessionId).expectStatus().isNoContent();

    assertThat(movedSince(before)).isEqualTo(moved("payAttempted", "paid"));
  }

  @Test
  void aDeclineNobodyReadsIsCountedWhenRedisDropsTheSession() throws Exception {
    stubSuccessfulCheckout();
    saga.answer(Status.PROCESSING);
    var sessionId = shippedSessionId();
    var before = funnel();
    pay(sessionId).expectStatus().isAccepted();

    saga.finish(sessionId, Status.DECLINED);
    dropFromRedis(sessionId);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(movedSince(before))
                    .isEqualTo(moved("payAttempted", "declined", "expired")));
  }

  @Test
  void aPaidSessionRedisDropsIsNotCountedExpired() throws Exception {
    stubSuccessfulCheckout();
    saga.answer(Status.PROCESSING);
    var sessionId = shippedSessionId();
    var before = funnel();
    pay(sessionId).expectStatus().isAccepted();

    // Paid, but the Saga hasn't ended the session.
    saga.finish(sessionId, Status.PAID);
    dropFromRedis(sessionId);

    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> assertThat(movedSince(before)).isEqualTo(moved("payAttempted", "paid")));
  }

  @Test
  void aPaymentTheSagaCouldNotStartIsNotCounted() {
    stubSuccessfulCheckout();
    var sessionId = shippedSessionId();
    var before = funnel();
    saga.unreachable();

    pay(sessionId).expectStatus().isEqualTo(503);

    assertThat(movedSince(before)).isEqualTo(moved());
  }

  /** The checkout Saga ends the session, once its Order is paid. */
  RestTestClient.ResponseSpec endAsOrchestration(String sessionId) {
    return http.post()
        .uri("/checkout/sessions/{id}/end", sessionId)
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("orchestration", "ORCHESTRATION")))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"customerId\": \"%s\"}".formatted(CUSTOMER_ID))
        .exchange();
  }

  /** Redis drops the session's key as it would once its time to live runs out. */
  void dropFromRedis(String sessionId) throws Exception {
    REDIS.execInContainer("redis-cli", "PEXPIRE", "checkout-session:" + sessionId, "1");
  }

  /** Each step's count now. */
  Map<String, Double> funnel() {
    var scrape =
        http.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    var counts = new LinkedHashMap<String, Double>();
    for (var step : STEPS) {
      var series =
          Pattern.compile(
              "(?m)^checkout_sessions_total\\{[^}]*\\bstep=\"" + step + "\"[^}]*\\} (\\S+)$");
      var values = series.matcher(scrape).results().map(m -> m.group(1)).toList();
      assertThat(values).as("step %s in the scrape", step).hasSize(1);
      counts.put(step, Double.parseDouble(values.getFirst()));
    }
    return counts;
  }

  /** How far each step's count moved since {@code before}. */
  Map<String, Double> movedSince(Map<String, Double> before) {
    var now = funnel();
    var moved = new LinkedHashMap<String, Double>();
    for (var step : STEPS) {
      moved.put(step, now.get(step) - before.get(step));
    }
    return moved;
  }

  /** Every step moved by as many times as it is named here, and the rest by none. */
  static Map<String, Double> moved(String... steps) {
    var moved = new LinkedHashMap<String, Double>();
    for (var step : STEPS) {
      moved.put(step, 0.0);
    }
    for (var step : steps) {
      moved.merge(step, 1.0, Double::sum);
    }
    return moved;
  }
}
