package com.ecomm.orchestration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.orchestration.application.port.in.CheckoutOutcome;
import com.ecomm.orchestration.application.port.in.CheckoutRequest;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.Discount;
import com.ecomm.orchestration.application.port.in.CheckoutRequest.Line;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The checkout Saga, run on the {@code checkout} task queue with its real activities, which call
 * WireMock stubs of Order Management, Payment, Inventory, Cart and Checkout with Orchestration's
 * own token. Retries' backoffs are skipped, so a step retried for minutes takes moments here.
 */
class CheckoutWorkflowTest extends OrchestrationTest {

  static final String CUSTOMER_ID = "customer-42";
  static final String ORDER_ID = "7f1c2a3b-0000-4000-8000-000000000001";
  static final String PAYMENT_ID = "9a8b7c6d-0000-4000-8000-000000000001";
  static final String RESERVATION_ID = "5e2d1c0b-0000-4000-8000-000000000001";
  static final String CORRELATION_ID = "corr-checkout-1";

  static final String COMMIT = "/reservations/" + RESERVATION_ID + "/commit";
  static final String STATUS = "/orders/" + ORDER_ID + "/status";
  static final String VOID = "/payments/" + PAYMENT_ID + "/void";

  @Autowired WorkflowClient temporal;
  @Autowired RestTestClient http;

  String sessionId;

  @BeforeEach
  void newSession() {
    sessionId = UUID.randomUUID().toString();
    stubToken();
  }

  @Test
  void theStepsRunInOrderAndThePaidOrderIsTheOutcome() {
    stubEveryStep();

    var outcome = checkout();

    assertThat(outcome).isEqualTo(new CheckoutOutcome(CheckoutOutcome.Status.PAID, ORDER_ID, null));
    assertThat(calls())
        .containsExactly(
            "POST /orders",
            "POST /payments",
            "POST " + COMMIT,
            "PATCH " + STATUS,
            "POST /carts/clear",
            "POST /checkout/sessions/" + sessionId + "/end");
  }

  @Test
  void eachCommandCarriesItsKeyFromTheRunAndEveryCallTheCorrelationId() {
    stubEveryStep();

    var run = start();
    run.getResult(CheckoutOutcome.class);

    var key = sessionId + ":" + run.getExecution().getRunId() + ":";
    assertThat(keyOf("POST", "/orders")).isEqualTo(key + "placeOrder");
    assertThat(keyOf("POST", "/payments")).isEqualTo(key + "authorizePayment");
    assertThat(keyOf("PATCH", STATUS)).isEqualTo(key + "markOrderPaid");
    assertThat(keyOf("POST", COMMIT)).isNull();
    assertThat(downstreamRequests())
        .allSatisfy(r -> assertThat(r.getHeader("X-Correlation-Id")).isEqualTo(CORRELATION_ID));
  }

  @Test
  void eachStepNamesTheCustomerAndCarriesTheSessionAsItStood() {
    stubEveryStep();

    checkout();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/orders"))
            .withHeader("Authorization", equalTo("Bearer orchestration-token"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42", "lines": [
                      {"variantId": "PHN-PIXEL-9", "quantity": 2,
                       "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}
                    ],
                     "discounts": [{"source": "COUPON", "couponCode": "WELCOME10",
                                    "amount": {"amountMinor": 15980, "currency": "EUR"}}],
                     "tax": {"amountMinor": 0, "currency": "EUR"}}
                    """)));
    // For the total Order Management gave the Order, not one worked out here.
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/payments"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42", "orderId": "%s", "paymentMethod": "tok_approve",
                     "amount": {"amountMinor": 143820, "currency": "EUR"}}
                    """
                        .formatted(ORDER_ID))));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(COMMIT))
            .withRequestBody(equalToJson("{\"customerId\": \"customer-42\"}")));
    DOWNSTREAM.verify(
        patchRequestedFor(urlEqualTo(STATUS))
            .withRequestBody(
                equalToJson("{\"customerId\": \"customer-42\", \"status\": \"PAID\"}")));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/carts/clear"))
            .withRequestBody(equalToJson("{\"customerId\": \"customer-42\"}")));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/checkout/sessions/" + sessionId + "/end"))
            .withRequestBody(equalToJson("{\"customerId\": \"customer-42\"}")));
  }

  @Test
  void aDeclineCancelsTheOrderAndGoesNoFurther() {
    stubEveryStep();
    stubAuthorization("DECLINED", "insufficient_funds");

    var outcome = checkout();

    assertThat(outcome)
        .isEqualTo(
            new CheckoutOutcome(CheckoutOutcome.Status.DECLINED, ORDER_ID, "insufficient_funds"));
    assertThat(calls()).containsExactly("POST /orders", "POST /payments", "PATCH " + STATUS);
    DOWNSTREAM.verify(
        patchRequestedFor(urlEqualTo(STATUS))
            .withRequestBody(matchingJsonPath("$.status", equalTo("CANCELLED"))));
  }

  @Test
  void aReservationThatNoLongerHoldsTheStockVoidsThePaymentAndCancelsTheOrder() {
    stubEveryStep();
    DOWNSTREAM.stubFor(
        post(COMMIT)
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody(
                        "{\"status\": 409, \"reservationExpired\": \"" + RESERVATION_ID + "\"}")));

    var outcome = checkout();

    assertThat(outcome)
        .isEqualTo(new CheckoutOutcome(CheckoutOutcome.Status.HOLD_EXPIRED, ORDER_ID, null));
    assertThat(calls())
        .containsExactly(
            "POST /orders", "POST /payments", "POST " + COMMIT, "POST " + VOID, "PATCH " + STATUS);
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(VOID))
            .withRequestBody(equalToJson("{\"customerId\": \"customer-42\"}")));
    DOWNSTREAM.verify(
        patchRequestedFor(urlEqualTo(STATUS))
            .withRequestBody(matchingJsonPath("$.status", equalTo("CANCELLED"))));
  }

  @Test
  void aPlacementThatFailsOnceIsRetriedWithTheSameKey() {
    stubEveryStep();
    stubInSequence(() -> post(urlEqualTo("/orders")), aResponse().withStatus(503), placedOrder());

    assertThat(checkout().status()).isEqualTo(CheckoutOutcome.Status.PAID);

    var keys =
        downstreamRequests().stream()
            .filter(r -> r.getUrl().equals("/orders"))
            .map(r -> r.getHeader("Idempotency-Key"))
            .toList();
    assertThat(keys).hasSize(2).doesNotContainNull();
    assertThat(keys.get(0)).isEqualTo(keys.get(1));
  }

  @Test
  void aPlacementOrderManagementRefusesFailsWithNothingToUndo() {
    stubEveryStep();
    DOWNSTREAM.stubFor(post("/orders").willReturn(aResponse().withStatus(400)));

    assertThat(checkout())
        .isEqualTo(new CheckoutOutcome(CheckoutOutcome.Status.FAILED, null, null));

    assertThat(calls()).containsExactly("POST /orders");
  }

  @Test
  void aGatewayThatKeepsFailingIsTriedFourTimesWithOneKeyThenTheOrderIsCancelled() {
    stubEveryStep();
    DOWNSTREAM.stubFor(post("/payments").willReturn(aResponse().withStatus(502)));

    assertThat(checkout())
        .isEqualTo(new CheckoutOutcome(CheckoutOutcome.Status.FAILED, ORDER_ID, null));

    var keys =
        downstreamRequests().stream()
            .filter(r -> r.getUrl().equals("/payments"))
            .map(r -> r.getHeader("Idempotency-Key"))
            .distinct()
            .toList();
    DOWNSTREAM.verify(4, postRequestedFor(urlEqualTo("/payments")));
    assertThat(keys).hasSize(1);
    DOWNSTREAM.verify(
        patchRequestedFor(urlEqualTo(STATUS))
            .withRequestBody(matchingJsonPath("$.status", equalTo("CANCELLED"))));
    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo(COMMIT)));
  }

  @Test
  void aFailingCompensationIsRetriedUntilItSucceeds() {
    stubEveryStep();
    stubAuthorization("DECLINED", "card_declined");
    stubInSequence(
        () -> statusChangeTo("CANCELLED"),
        aResponse().withStatus(503),
        okJson("{\"id\": \"" + ORDER_ID + "\"}"));

    assertThat(checkout().status()).isEqualTo(CheckoutOutcome.Status.DECLINED);

    DOWNSTREAM.verify(2, patchRequestedFor(urlEqualTo(STATUS)));
  }

  @Test
  void aCartThatCantBeClearedLeavesTheOrderPaidAndTheSessionIsStillEnded() {
    stubEveryStep();
    DOWNSTREAM.stubFor(post("/carts/clear").willReturn(aResponse().withStatus(503)));

    var run = start();

    assertThat(run.getResult(CheckoutOutcome.class))
        .isEqualTo(new CheckoutOutcome(CheckoutOutcome.Status.PAID, ORDER_ID, null));
    assertThat(run.describe().getMemo("outcome", CheckoutOutcome.class))
        .isEqualTo(new CheckoutOutcome(CheckoutOutcome.Status.PAID, ORDER_ID, null));
    assertThat(DOWNSTREAM.findAll(postRequestedFor(urlEqualTo("/carts/clear"))))
        .hasSizeGreaterThan(1);
    DOWNSTREAM.verify(1, postRequestedFor(urlEqualTo("/checkout/sessions/" + sessionId + "/end")));
    DOWNSTREAM.verify(
        0,
        patchRequestedFor(urlEqualTo(STATUS))
            .withRequestBody(matchingJsonPath("$.status", equalTo("CANCELLED"))));
  }

  @Test
  void anOrderThatCantBeMarkedPaidAtFirstIsMarkedPaidOnceItCan() {
    stubEveryStep();
    stubInSequence(
        () -> statusChangeTo("PAID"),
        aResponse().withStatus(500),
        okJson("{\"id\": \"" + ORDER_ID + "\"}"));

    assertThat(checkout().status()).isEqualTo(CheckoutOutcome.Status.PAID);

    DOWNSTREAM.verify(2, patchRequestedFor(urlEqualTo(STATUS)));
  }

  @Test
  void payingTheSessionAgainAfterADeclineSendsKeysOfItsOwn() {
    stubEveryStep();
    stubAuthorization("DECLINED", "card_declined");
    var declined = start("tok_decline");
    assertThat(declined.getResult(CheckoutOutcome.class).status())
        .isEqualTo(CheckoutOutcome.Status.DECLINED);
    var firstKey = keyOf("POST", "/orders");
    DOWNSTREAM.resetRequests();

    stubAuthorization("AUTHORIZED", null);
    var paid = start("tok_approve");

    assertThat(paid.getResult(CheckoutOutcome.class).status())
        .isEqualTo(CheckoutOutcome.Status.PAID);
    assertThat(paid.getExecution().getWorkflowId()).isEqualTo(sessionId);
    assertThat(keyOf("POST", "/orders"))
        .isEqualTo(sessionId + ":" + paid.getExecution().getRunId() + ":placeOrder")
        .isNotEqualTo(firstKey);
  }

  @Test
  void outcomesCompensationsAndStepFailuresAreCountedBesideTemporalsOwnMetrics() {
    stubEveryStep();
    stubInSequence(() -> post(urlEqualTo("/orders")), aResponse().withStatus(503), placedOrder());
    checkout();
    sessionId = UUID.randomUUID().toString();
    stubAuthorization("DECLINED", "card_declined");
    checkout();

    // Temporal reports to Micrometer every 10 seconds.
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(prometheus())
                    .containsPattern("checkout_workflows_total\\{[^}]*outcome=\"PAID\"")
                    .containsPattern("checkout_workflows_total\\{[^}]*outcome=\"DECLINED\"")
                    .containsPattern("checkout_compensations_total\\{[^}]*kind=\"cancelOrder\"")
                    .containsPattern("checkout_step_failures_total\\{[^}]*step=\"placeOrder\"")
                    .contains("temporal_workflow_completed_total"));
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

  // --- running the Saga ---

  CheckoutOutcome checkout() {
    return start().getResult(CheckoutOutcome.class);
  }

  /** Starts the Saga for {@link #sessionId}, as Checkout does, paid with {@code tok_approve}. */
  WorkflowStub start() {
    return start("tok_approve");
  }

  WorkflowStub start(String paymentMethod) {
    var stub =
        temporal.newUntypedWorkflowStub(
            "checkout",
            WorkflowOptions.newBuilder().setTaskQueue("checkout").setWorkflowId(sessionId).build());
    stub.start(request(paymentMethod));
    return stub;
  }

  CheckoutRequest request(String paymentMethod) {
    return new CheckoutRequest(
        sessionId,
        CUSTOMER_ID,
        List.of(new Line("PHN-PIXEL-9", 2, eur(79900))),
        List.of(new Discount("COUPON", "WELCOME10", null, null, eur(15980))),
        eur(0),
        RESERVATION_ID,
        paymentMethod,
        CORRELATION_ID);
  }

  static Amount eur(long amountMinor) {
    return new Amount(amountMinor, "EUR");
  }

  // --- stubs ---

  static void stubToken() {
    DOWNSTREAM.stubFor(
        post(TOKEN_PATH)
            .willReturn(
                okJson(
                    """
                    {"access_token": "orchestration-token", "token_type": "Bearer", "expires_in": 3600}
                    """)));
  }

  /** Every step succeeds: the Order is placed for 1438.20 EUR and its Payment authorized. */
  static void stubEveryStep() {
    DOWNSTREAM.stubFor(post("/orders").willReturn(placedOrder()));
    stubAuthorization("AUTHORIZED", null);
    DOWNSTREAM.stubFor(post(COMMIT).willReturn(reservation("COMMITTED")));
    DOWNSTREAM.stubFor(statusChange().willReturn(okJson("{\"id\": \"" + ORDER_ID + "\"}")));
    DOWNSTREAM.stubFor(post(VOID).willReturn(payment("VOIDED", null, 200)));
    DOWNSTREAM.stubFor(post("/carts/clear").willReturn(aResponse().withStatus(204)));
    DOWNSTREAM.stubFor(
        post(urlPathMatching("/checkout/sessions/[^/]+/end"))
            .willReturn(aResponse().withStatus(204)));
  }

  static MappingBuilder statusChange() {
    return patch(urlEqualTo(STATUS));
  }

  static MappingBuilder statusChangeTo(String status) {
    return statusChange().withRequestBody(matchingJsonPath("$.status", equalTo(status)));
  }

  static ResponseDefinitionBuilder placedOrder() {
    return aResponse()
        .withStatus(201)
        .withHeader("Content-Type", "application/json")
        .withBody(
            """
            {"id": "%s", "status": "PLACED", "total": {"amountMinor": 143820, "currency": "EUR"}}
            """
                .formatted(ORDER_ID));
  }

  static void stubAuthorization(String status, String declineReason) {
    DOWNSTREAM.stubFor(post("/payments").willReturn(payment(status, declineReason, 201)));
  }

  static ResponseDefinitionBuilder payment(String status, String declineReason, int httpStatus) {
    return aResponse()
        .withStatus(httpStatus)
        .withHeader("Content-Type", "application/json")
        .withBody(
            """
            {"id": "%s", "orderId": "%s", "status": "%s", "declineReason": %s}
            """
                .formatted(
                    PAYMENT_ID,
                    ORDER_ID,
                    status,
                    declineReason == null ? "null" : "\"" + declineReason + "\""));
  }

  static ResponseDefinitionBuilder reservation(String status) {
    return okJson(
        """
        {"id": "%s", "customerId": "%s", "status": "%s"}
        """
            .formatted(RESERVATION_ID, CUSTOMER_ID, status));
  }

  /** {@code request} is answered {@code first}, then {@code then} from the next one on. */
  static void stubInSequence(
      Supplier<MappingBuilder> request,
      ResponseDefinitionBuilder first,
      ResponseDefinitionBuilder then) {
    var scenario = UUID.randomUUID().toString();
    DOWNSTREAM.stubFor(
        request
            .get()
            .inScenario(scenario)
            .whenScenarioStateIs(STARTED)
            .willReturn(first)
            .willSetStateTo("then")
            .atPriority(1));
    DOWNSTREAM.stubFor(
        request
            .get()
            .inScenario(scenario)
            .whenScenarioStateIs("then")
            .willReturn(then)
            .atPriority(1));
  }

  // --- what the stubs were called with ---

  /** Every call to another service, not to Keycloak, oldest first. */
  static List<LoggedRequest> downstreamRequests() {
    return DOWNSTREAM.getAllServeEvents().stream()
        .map(ServeEvent::getRequest)
        .filter(r -> !r.getUrl().equals(TOKEN_PATH))
        .sorted(Comparator.comparing(LoggedRequest::getLoggedDate))
        .toList();
  }

  static List<String> calls() {
    return downstreamRequests().stream().map(r -> r.getMethod() + " " + r.getUrl()).toList();
  }

  /** The {@code Idempotency-Key} of the one call {@code method path}; null when it sent none. */
  static String keyOf(String method, String path) {
    var matching =
        downstreamRequests().stream()
            .filter(r -> r.getMethod().getName().equals(method) && r.getUrl().equals(path))
            .toList();
    assertThat(matching).hasSize(1);
    return matching.getFirst().getHeader("Idempotency-Key");
  }
}
