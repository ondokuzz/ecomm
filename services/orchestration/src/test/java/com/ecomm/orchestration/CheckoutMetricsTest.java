package com.ecomm.orchestration;

import static com.ecomm.orchestration.CheckoutWorkflowTest.CORRELATION_ID;
import static com.ecomm.orchestration.CheckoutWorkflowTest.CUSTOMER_ID;
import static com.ecomm.orchestration.CheckoutWorkflowTest.RESERVATION_ID;
import static com.ecomm.orchestration.CheckoutWorkflowTest.eur;
import static com.ecomm.orchestration.CheckoutWorkflowTest.placedOrder;
import static com.ecomm.orchestration.CheckoutWorkflowTest.stubAuthorization;
import static com.ecomm.orchestration.CheckoutWorkflowTest.stubEveryStep;
import static com.ecomm.orchestration.CheckoutWorkflowTest.stubInSequence;
import static com.ecomm.orchestration.CheckoutWorkflowTest.stubToken;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.orchestration.application.port.in.CheckoutOutcome;
import com.ecomm.orchestration.application.port.in.CheckoutRequest;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.Line;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The checkout Saga's counters are reported at 0 from the start, for every outcome, compensation
 * and step, so that Prometheus has a sample before a counter's first increment and Grafana's {@code
 * increase()} counts it. A run then increments that same series rather than starting another. The
 * context is fresh, so nothing has been counted before the first test, and the tests run in order.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CheckoutMetricsTest extends OrchestrationTest {

  @Autowired WorkflowClient temporal;
  @Autowired RestTestClient http;

  static final List<String> OUTCOMES = List.of("PAID", "DECLINED", "HOLD_EXPIRED", "FAILED");
  static final List<String> COMPENSATIONS = List.of("cancelOrder", "voidPayment");
  static final List<String> STEPS =
      List.of(
          "placeOrder",
          "authorizePayment",
          "awaitSettlement",
          "commitReservation",
          "markOrderPaid",
          "cancelOrder",
          "voidPayment",
          "clearCart",
          "endCheckoutSession");

  @Test
  @Order(1)
  void everyOutcomeCompensationAndStepIsReportedAtZeroBeforeAnyCheckout() {
    var metrics = prometheus();

    for (var outcome : OUTCOMES) {
      assertThat(samples(metrics, "checkout_workflows_total", "outcome", outcome))
          .as("outcome %s", outcome)
          .containsExactly(0.0);
    }
    for (var kind : COMPENSATIONS) {
      assertThat(samples(metrics, "checkout_compensations_total", "kind", kind))
          .as("compensation %s", kind)
          .containsExactly(0.0);
    }
    for (var step : STEPS) {
      assertThat(samples(metrics, "checkout_step_failures_total", "step", step))
          .as("step %s", step)
          .containsExactly(0.0);
    }
  }

  @Test
  @Order(2)
  void aCheckoutCountsOnTheSeriesReportedAtZero() {
    stubEveryStep();
    stubInSequence(() -> post(urlEqualTo("/orders")), aResponse().withStatus(503), placedOrder());
    stubAuthorization("DECLINED", "card_declined");
    stubToken();

    var outcome = checkout();
    assertThat(outcome.status()).isEqualTo(CheckoutOutcome.Status.DECLINED);

    // Temporal reports to Micrometer every 10 seconds.
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              var metrics = prometheus();
              assertThat(samples(metrics, "checkout_workflows_total", "outcome", "DECLINED"))
                  .containsExactly(1.0);
              assertThat(samples(metrics, "checkout_compensations_total", "kind", "cancelOrder"))
                  .containsExactly(1.0);
              assertThat(samples(metrics, "checkout_step_failures_total", "step", "placeOrder"))
                  .containsExactly(1.0);
            });
  }

  CheckoutOutcome checkout() {
    var sessionId = UUID.randomUUID().toString();
    var stub =
        temporal.newUntypedWorkflowStub(
            "checkout",
            WorkflowOptions.newBuilder().setTaskQueue("checkout").setWorkflowId(sessionId).build());
    stub.start(
        new CheckoutRequest(
            sessionId,
            CUSTOMER_ID,
            List.of(new Line("PHN-PIXEL-9", 1, eur(79900))),
            List.of(),
            eur(0),
            CheckoutWorkflowTest.ADDRESS,
            CheckoutWorkflowTest.EXPRESS,
            RESERVATION_ID,
            "tok_approve",
            CORRELATION_ID));
    return stub.getResult(CheckoutOutcome.class);
  }

  String prometheus() {
    return http.get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  /** The value of every series of {@code metric} whose {@code label} is {@code value}. */
  static List<Double> samples(String metrics, String metric, String label, String value) {
    var series =
        Pattern.compile(
            "(?m)^"
                + metric
                + "\\{[^}]*\\b"
                + label
                + "=\""
                + Pattern.quote(value)
                + "\"[^}]*\\} (\\S+)$");
    return series.matcher(metrics).results().map(m -> Double.parseDouble(m.group(1))).toList();
  }
}
