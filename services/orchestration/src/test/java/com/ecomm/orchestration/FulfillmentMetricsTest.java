package com.ecomm.orchestration;

import static com.ecomm.orchestration.CheckoutMetricsTest.samples;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The fulfillment Saga's counters are reported at 0 from the start, for every outcome, kind of hold
 * and step, so that Grafana's {@code increase()} counts the first of each. The context is fresh, so
 * nothing has been counted before.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class FulfillmentMetricsTest extends OrchestrationTest {

  @Autowired RestTestClient http;

  static final List<String> OUTCOMES =
      List.of("DELIVERED", "CAPTURE_REFUSED", "DELIVERY_UNCONFIRMED", "FAILED");
  static final List<String> HOLDS = List.of("captureDeclined", "captureFailed");
  static final List<String> STEPS =
      List.of(
          "createShipment",
          "capturePayment",
          "markOrderFulfilled",
          "dispatchShipment",
          "markOrderShipped",
          "markOrderDelivered",
          "holdShipment");

  @Test
  void everyOutcomeHoldAndStepIsReportedAtZeroBeforeAnyFulfillment() {
    var metrics =
        http.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    for (var outcome : OUTCOMES) {
      assertThat(samples(metrics, "fulfillment_workflows_total", "outcome", outcome))
          .as("outcome %s", outcome)
          .containsExactly(0.0);
    }
    for (var kind : HOLDS) {
      assertThat(samples(metrics, "fulfillment_holds_total", "kind", kind))
          .as("hold %s", kind)
          .containsExactly(0.0);
    }
    for (var step : STEPS) {
      assertThat(samples(metrics, "fulfillment_step_failures_total", "step", step))
          .as("step %s", step)
          .containsExactly(0.0);
    }
  }
}
