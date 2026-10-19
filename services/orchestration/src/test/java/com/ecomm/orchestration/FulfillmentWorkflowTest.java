package com.ecomm.orchestration;

import static com.ecomm.orchestration.CheckoutWorkflowTest.downstreamRequests;
import static com.ecomm.orchestration.CheckoutWorkflowTest.stubInSequence;
import static com.ecomm.orchestration.CheckoutWorkflowTest.stubToken;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.orchestration.application.port.in.FulfillmentOutcome;
import com.ecomm.orchestration.application.port.in.FulfillmentOutcome.Status;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.grpc.StatusRuntimeException;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowException;
import io.temporal.testserver.TestServer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * The fulfillment Saga, from Kafka in to HTTP out: Orchestration's real consumers read Order and
 * Shipment events from Kafka, and its real activities call WireMock stubs of Fulfillment &
 * Shipping, Payment and Order Management with Orchestration's own token.
 *
 * <p>The class has a test server of its own, with time skipping off, so the Saga's waits pass in
 * real time: its wait for delivery, {@link #DELIVERY_DEADLINE} here, doesn't end before the test's
 * Shipment events arrive. Its own, too, because a run terminated while a workflow task is
 * outstanding leaves the test server's time skipping locked for good (the task's completion is
 * refused, and its lock never released): on the shared server, every later class's retries and
 * timers would then wait in real time.
 *
 * <p>Each topic has one partition, consumed in order, so once a later event has visibly been
 * handled, every event sent before it has been too: that is how a test sees that an event started
 * or woke nothing.
 */
// This class's Saga runs on its own test server, where time skipping is locked from the start.
@TestPropertySource(
    properties = "spring.temporal.connection.target=localhost:${ecomm.test.real-time-port}")
class FulfillmentWorkflowTest extends OrchestrationTest {

  static final String CUSTOMER_ID = "customer-42";
  static final String SHIPMENT_ID = "0b8e6f2a-1c3d-4e5f-a6b7-c8d9e0f1a2b3";
  static final String PAYMENT_ID = "9a8b7c6d-0000-4000-8000-000000000002";

  static final String DISPATCH = "/shipments/" + SHIPMENT_ID + "/dispatch";
  static final String HOLD = "/shipments/" + SHIPMENT_ID + "/hold";
  static final String CAPTURE = "/payments/" + PAYMENT_ID + "/capture";

  static final int REAL_TIME_PORT = freePort();

  static {
    TestServer.createPortBoundServer(REAL_TIME_PORT, true);
  }

  @DynamicPropertySource
  static void realTimeServer(DynamicPropertyRegistry registry) {
    registry.add("ecomm.test.real-time-port", () -> REAL_TIME_PORT);
  }

  @Autowired WorkflowClient temporal;
  @Autowired KafkaListenerEndpointRegistry listeners;

  String orderId;
  String correlationId;

  /** Every Order whose PAID event this test sent: their Sagas end with the test. */
  final List<String> ordersPaid = new java.util.ArrayList<>();

  @BeforeEach
  void newOrder() {
    orderId = UUID.randomUUID().toString();
    ordersPaid.add(orderId);
    correlationId = "corr-" + orderId;
    stubToken();
    // The Order consumer starts from the latest offset, so an event sent before it is assigned
    // its partition would be missed.
    await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () ->
                listeners.getListenerContainers().stream()
                    .allMatch(
                        c ->
                            c.getAssignedPartitions() != null
                                && !c.getAssignedPartitions().isEmpty()));
  }

  /** Ends every run the test left waiting, so none can call the next test's stubs. */
  @AfterEach
  void endTheRun() {
    for (var order : ordersPaid) {
      var id = "fulfillment:" + order;
      try {
        temporal.newUntypedWorkflowStub(id).terminate("the test is over");
      } catch (WorkflowException | StatusRuntimeException e) {
        // It ended, or never started.
      }
    }
  }

  @Test
  void aPaidOrderWithAnAddressStartsItsSagaWhichCreatesTheShipment() {
    stubEveryStep();

    paid();

    awaitCalls(1);
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/shipments"))
            .withHeader("Authorization", equalTo("Bearer orchestration-token"))
            .withHeader("Idempotency-Key", equalTo(keyOf("createShipment")))
            .withHeader("X-Correlation-Id", equalTo(correlationId))
            .withRequestBody(
                equalToJson(
                    """
                    {"orderId": "%s", "customerId": "customer-42",
                     "lines": [{"variantId": "PHN-PIXEL-9", "quantity": 1},
                               {"variantId": "ACC-CASE", "quantity": 2}],
                     "shippingAddress": {"recipientName": "Erika Mustermann",
                                         "line1": "Hauptstraße 5", "postcode": "10115",
                                         "city": "Berlin", "country": "DE"},
                     "shippingOption": {"id": "EXPRESS", "carrier": "Mock Carrier",
                                        "cost": {"amountMinor": 999, "currency": "EUR"}}}
                    """
                        .formatted(orderId))));
    assertThat(calls()).containsExactly("POST /shipments");
  }

  @Test
  void thePackedShipmentIsCapturedFulfilledDispatchedAndShippedThenDeliveredInOrder() {
    stubEveryStep();
    paid();
    awaitCalls(1);

    shipment(1, "AWAITING_PICK");
    shipment(2, "PICKED");
    shipment(3, "PACKED");
    awaitCalls(6);
    shipment(4, "SHIPPED");
    shipment(5, "DELIVERED");

    assertThat(outcome()).isEqualTo(new FulfillmentOutcome(Status.DELIVERED, SHIPMENT_ID, null));
    assertThat(calls())
        .containsExactly(
            "POST /shipments",
            "GET " + readPayments(),
            "POST " + CAPTURE,
            "PATCH " + status() + " FULFILLED",
            "POST " + DISPATCH,
            "PATCH " + status() + " SHIPPED",
            "PATCH " + status() + " DELIVERED");
    assertThat(keys())
        .containsExactly(
            keyOf("createShipment"),
            null,
            keyOf("capturePayment"),
            keyOf("markOrderFulfilled"),
            keyOf("dispatchShipment"),
            keyOf("markOrderShipped"),
            keyOf("markOrderDelivered"));
    assertThat(downstreamRequests())
        .allSatisfy(r -> assertThat(r.getHeader("X-Correlation-Id")).isEqualTo(correlationId));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(CAPTURE))
            .withRequestBody(equalToJson("{\"customerId\": \"customer-42\"}")));
  }

  @Test
  void theSameEventTwiceAndAReplayAfterTheSagaClosedStartNothingMore() {
    stubEveryStep();
    stubCaptureDeclined();
    paid();
    paid();
    awaitCalls(1);
    var firstRun = runIdOf(workflowId());
    shipment(3, "PACKED");
    assertThat(outcome().status()).isEqualTo(Status.CAPTURE_REFUSED);

    paid();
    var marker = anotherPaidOrder();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(shipmentsCreatedFor(marker)).isEqualTo(1));
    assertThat(shipmentsCreatedFor(orderId)).isEqualTo(1);
    assertThat(runIdOf(workflowId())).isEqualTo(firstRun);
  }

  @Test
  void anOrderPaidWithoutAnAddressAsBeforeSprint5StartsNothing() {
    stubEveryStep();
    FulfillmentEvents.order(orderId, CUSTOMER_ID, 2, "PAID", false, correlationId);
    var marker = anotherPaidOrder();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(shipmentsCreatedFor(marker)).isEqualTo(1));
    assertThat(shipmentsCreatedFor(orderId)).isZero();
    assertThat(isStarted(workflowId())).isFalse();
  }

  @Test
  void anOrderNotYetPaidStartsNothing() {
    stubEveryStep();
    FulfillmentEvents.order(orderId, CUSTOMER_ID, 1, "PLACED", true, correlationId);
    var marker = anotherPaidOrder();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(shipmentsCreatedFor(marker)).isEqualTo(1));
    assertThat(isStarted(workflowId())).isFalse();
  }

  @Test
  void aShipmentThatExistsAlreadyIsReadByOrderAndTakenAsCreated() {
    stubEveryStep();
    stubShipmentExists("AWAITING_PICK", null);
    paid();
    awaitCalls(2);

    shipment(3, "PACKED");

    awaitCalls(7);
    assertThat(calls()).contains("POST " + DISPATCH);
  }

  /**
   * A PAID event replayed once Temporal no longer keeps the first run (7 days) starts a second one,
   * which finds the Shipment held by the first: it ends as that one did, and changes nothing.
   */
  @Test
  void aShipmentFoundOnHoldEndsTheSagaAsCaptureRefusedWithItsReason() {
    stubEveryStep();
    stubShipmentExists("ON_HOLD", "Capture declined: authorization_expired");

    paid();

    assertThat(outcome())
        .isEqualTo(
            new FulfillmentOutcome(
                Status.CAPTURE_REFUSED, SHIPMENT_ID, "Capture declined: authorization_expired"));
    assertThat(calls()).containsExactly("POST /shipments", "GET /shipments?orderId=" + orderId);
  }

  /** One found packed, its PACKED event long consumed, goes on at once: it is captured once. */
  @Test
  void aShipmentFoundPackedIsCapturedFulfilledDispatchedAndShippedWithoutAnEvent() {
    stubEveryStep();
    stubShipmentExists("PACKED", null);

    paid();

    awaitCalls(7);
    assertThat(calls())
        .containsExactly(
            "POST /shipments",
            "GET /shipments?orderId=" + orderId,
            "GET " + readPayments(),
            "POST " + CAPTURE,
            "PATCH " + status() + " FULFILLED",
            "POST " + DISPATCH,
            "PATCH " + status() + " SHIPPED");
  }

  /**
   * One found shipped was dispatched once its Payment was captured, and its Order marked shipped,
   * by a run that ended before delivery was confirmed: this one only awaits delivery.
   */
  @Test
  void aShipmentFoundShippedOnlyAwaitsDeliveryThenMarksTheOrderDelivered() {
    stubEveryStep();
    stubShipmentExists("SHIPPED", null);
    paid();
    awaitCalls(2);

    shipment(5, "DELIVERED");

    assertThat(outcome()).isEqualTo(new FulfillmentOutcome(Status.DELIVERED, SHIPMENT_ID, null));
    assertThat(calls())
        .containsExactly(
            "POST /shipments",
            "GET /shipments?orderId=" + orderId,
            "PATCH " + status() + " DELIVERED");
  }

  /**
   * One found delivered may have been delivered after its first run's deadline, its Order still
   * shipped, or within it, its Order delivered already: Order Management refuses to deliver it
   * twice, and that refusal means it is.
   */
  @Test
  void aShipmentFoundDeliveredMarksTheOrderDeliveredUnlessItIsAlready() {
    stubEveryStep();
    stubShipmentExists("DELIVERED", null);
    DOWNSTREAM.stubFor(
        statusChangeTo("DELIVERED")
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody(
                        "{\"status\": 409, \"detail\": \"an order can't go from DELIVERED to"
                            + " DELIVERED\"}")));

    paid();

    assertThat(outcome()).isEqualTo(new FulfillmentOutcome(Status.DELIVERED, SHIPMENT_ID, null));
    assertThat(calls())
        .containsExactly(
            "POST /shipments",
            "GET /shipments?orderId=" + orderId,
            "PATCH " + status() + " DELIVERED");
  }

  /**
   * Its PACKED event never comes, dead-lettered say: the Saga reads its Shipment again once each
   * {@link #PACKING_CHECK} passes without packing, and goes on once the read says it is packed.
   */
  @Test
  void aPackingWhoseEventNeverComesIsFoundByReadingTheShipmentAgain() {
    stubEveryStep();
    DOWNSTREAM.stubFor(
        get(urlEqualTo("/shipments?orderId=" + orderId))
            .willReturn(
                okJson(
                    "{\"id\": \"%s\", \"orderId\": \"%s\", \"status\": \"PACKED\"}"
                        .formatted(SHIPMENT_ID, orderId))));
    paid();
    awaitCalls(1);
    var created = Instant.now();

    await()
        .atMost(PACKING_CHECK.plusSeconds(30))
        .untilAsserted(() -> assertThat(calls()).hasSizeGreaterThanOrEqualTo(7));
    assertThat(calls())
        .containsExactly(
            "POST /shipments",
            "GET /shipments?orderId=" + orderId,
            "GET " + readPayments(),
            "POST " + CAPTURE,
            "PATCH " + status() + " FULFILLED",
            "POST " + DISPATCH,
            "PATCH " + status() + " SHIPPED");
    assertThat(Duration.between(created, Instant.now()))
        .isGreaterThanOrEqualTo(PACKING_CHECK.minusSeconds(1));
  }

  @Test
  void aShipmentWhoseProductsAreNotKnownYetIsCreatedOnceTheyAre() {
    stubEveryStep();
    stubInSequence(
        () -> post(urlEqualTo("/shipments")),
        aResponse()
            .withStatus(503)
            .withHeader("Retry-After", "1")
            .withBody("{\"reason\": \"productsNotKnownYet\"}"),
        shipmentCreated());

    paid();

    awaitCalls(2);
    assertThat(keys()).containsExactly(keyOf("createShipment"), keyOf("createShipment"));
  }

  @Test
  void duplicateAndStaleShipmentEventsAreIgnored() {
    stubEveryStep();
    // Creating the Shipment takes a while, so the events below are all waiting for the Saga
    // when it is done: if it took the stale PICKED after PACKED, it would wait for packing still.
    DOWNSTREAM.stubFor(
        post("/shipments").atPriority(1).willReturn(shipmentCreated().withFixedDelay(3000)));
    paid();
    await().atMost(Duration.ofSeconds(30)).until(() -> isStarted(workflowId()));

    shipment(3, "PACKED");
    shipment(3, "PACKED");
    shipment(2, "PICKED");

    awaitCalls(6);
    shipment(5, "DELIVERED");
    shipment(5, "DELIVERED");
    assertThat(outcome().status()).isEqualTo(Status.DELIVERED);
    assertThat(calls()).filteredOn(c -> c.startsWith("POST " + CAPTURE)).hasSize(1);
  }

  @Test
  void aDeclinedCaptureHoldsTheShipmentAndLeavesTheOrderPaid() {
    stubEveryStep();
    stubCaptureDeclined();
    paid();
    awaitCalls(1);

    shipment(3, "PACKED");

    assertThat(outcome())
        .isEqualTo(
            new FulfillmentOutcome(
                Status.CAPTURE_REFUSED, SHIPMENT_ID, "Capture declined: authorization_expired"));
    assertThat(calls())
        .containsExactly(
            "POST /shipments", "GET " + readPayments(), "POST " + CAPTURE, "POST " + HOLD);
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(HOLD))
            .withHeader("Idempotency-Key", equalTo(keyOf("holdShipment")))
            .withRequestBody(
                equalToJson("{\"reason\": \"Capture declined: authorization_expired\"}")));
  }

  @Test
  void anOrderWithNoAuthorizedPaymentHasItsShipmentHeld() {
    stubEveryStep();
    DOWNSTREAM.stubFor(
        readPaymentsRequest()
            .atPriority(1)
            .willReturn(
                okJson("[{\"id\": \"%s\", \"status\": \"VOIDED\"}]".formatted(PAYMENT_ID))));
    paid();
    awaitCalls(1);

    shipment(3, "PACKED");

    assertThat(outcome().status()).isEqualTo(Status.CAPTURE_REFUSED);
    assertThat(calls()).containsExactly("POST /shipments", "GET " + readPayments(), "POST " + HOLD);
  }

  @Test
  void aCaptureRetriedAfterAGatewayFailureFindsThePaymentOnce() {
    stubEveryStep();
    stubInSequence(
        () -> post(urlEqualTo(CAPTURE)),
        aResponse().withStatus(502),
        okJson("{\"id\": \"%s\", \"status\": \"CAPTURED\"}".formatted(PAYMENT_ID)));
    paid();
    awaitCalls(1);

    shipment(3, "PACKED");

    awaitCalls(7);
    assertThat(calls())
        .startsWith(
            "POST /shipments",
            "GET " + readPayments(),
            "POST " + CAPTURE,
            "POST " + CAPTURE,
            "PATCH " + status() + " FULFILLED");
    assertThat(keys().get(2)).isEqualTo(keys().get(3)).isEqualTo(keyOf("capturePayment"));
  }

  /**
   * The gateway captured during an attempt whose answer was lost, and every later attempt failed:
   * the Payment, read again, is captured, so the Order is fulfilled rather than held. Takes the
   * capture's minute of retries, in real time.
   */
  @Test
  void aCaptureThatKeepsFailingButWasMadeAfterAllGoesOnAsCaptured() {
    stubEveryStep();
    DOWNSTREAM.stubFor(post(CAPTURE).atPriority(1).willReturn(aResponse().withStatus(502)));
    stubInSequence(
        this::readPaymentsRequest,
        okJson("[{\"id\": \"%s\", \"status\": \"AUTHORIZED\"}]".formatted(PAYMENT_ID)),
        okJson("[{\"id\": \"%s\", \"status\": \"CAPTURED\"}]".formatted(PAYMENT_ID)));
    paid();
    awaitCalls(1);

    shipment(3, "PACKED");

    await()
        .atMost(Duration.ofSeconds(120))
        .untilAsserted(() -> assertThat(calls()).contains("POST " + DISPATCH));
    assertThat(calls())
        .containsSubsequence(
            "GET " + readPayments(),
            "POST " + CAPTURE,
            "GET " + readPayments(),
            "PATCH " + status() + " FULFILLED")
        .doesNotContain("POST " + HOLD);
  }

  @Test
  void anOrderManagementThatAnswers503OnFulfilledIsRetriedUntilItSucceeds() {
    stubEveryStep();
    stubInSequence(
        () -> statusChangeTo("FULFILLED"),
        aResponse().withStatus(503),
        okJson("{\"id\": \"%s\"}".formatted(orderId)));
    paid();
    awaitCalls(1);

    shipment(3, "PACKED");

    awaitCalls(7);
    assertThat(calls())
        .containsSubsequence(
            "PATCH " + status() + " FULFILLED",
            "PATCH " + status() + " FULFILLED",
            "POST " + DISPATCH,
            "PATCH " + status() + " SHIPPED");
    assertThat(keys().get(3)).isEqualTo(keys().get(4)).isEqualTo(keyOf("markOrderFulfilled"));
  }

  @Test
  void aDeliveryNeverConfirmedEndsTheSagaAtTheDeliveryDeadline() {
    stubEveryStep();
    paid();
    awaitCalls(1);
    shipment(3, "PACKED");
    awaitCalls(6);
    var shipped = Instant.now();

    assertThat(outcome())
        .isEqualTo(new FulfillmentOutcome(Status.DELIVERY_UNCONFIRMED, SHIPMENT_ID, null));
    assertThat(Duration.between(shipped, Instant.now()))
        .isGreaterThanOrEqualTo(DELIVERY_DEADLINE.minusSeconds(1));
    assertThat(calls()).doesNotContain("PATCH " + status() + " DELIVERED");
  }

  @Test
  void aShipmentEventForNoRunningSagaIsSkippedNotDeadLettered() {
    stubEveryStep();
    var unknownShipment = UUID.randomUUID().toString();
    FulfillmentEvents.shipment(unknownShipment, orderId, CUSTOMER_ID, 3, "PACKED");
    paid();
    awaitCalls(1);

    // Shipment events after the skipped one still wake their Saga.
    shipment(3, "PACKED");

    awaitCalls(6);
    assertThat(Kafka.recordsKeyed("orchestration.shipments.dlt", unknownShipment)).isEmpty();
  }

  // --- events ---

  /** The Order's {@code PAID} event, as Order Management publishes it once checkout paid it. */
  void paid() {
    FulfillmentEvents.order(orderId, CUSTOMER_ID, 2, "PAID", true, correlationId);
  }

  /** Another Order, paid: once its Shipment is created, every event before it has been handled. */
  String anotherPaidOrder() {
    var other = UUID.randomUUID().toString();
    ordersPaid.add(other);
    FulfillmentEvents.order(other, CUSTOMER_ID, 2, "PAID", true, "corr-" + other);
    return other;
  }

  void shipment(long version, String status) {
    FulfillmentEvents.shipment(SHIPMENT_ID, orderId, CUSTOMER_ID, version, status);
  }

  // --- the run ---

  String workflowId() {
    return "fulfillment:" + orderId;
  }

  FulfillmentOutcome outcome() {
    await().atMost(Duration.ofSeconds(30)).until(() -> isStarted(workflowId()));
    try {
      return temporal
          .newUntypedWorkflowStub(workflowId())
          .getResult(30, TimeUnit.SECONDS, FulfillmentOutcome.class);
    } catch (TimeoutException e) {
      throw new AssertionError("The Saga hadn't ended after 30 seconds", e);
    }
  }

  /** The {@code Idempotency-Key} the step {@code step} of this Order's run sends. */
  String keyOf(String step) {
    return workflowId() + ":" + runIdOf(workflowId()) + ":" + step;
  }

  String runIdOf(String workflowId) {
    return describe(workflowId).getWorkflowExecutionInfo().getExecution().getRunId();
  }

  boolean isStarted(String workflowId) {
    try {
      describe(workflowId);
      return true;
    } catch (StatusRuntimeException e) {
      return false;
    }
  }

  private io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse describe(
      String workflowId) {
    return temporal
        .getWorkflowServiceStubs()
        .blockingStub()
        .describeWorkflowExecution(
            DescribeWorkflowExecutionRequest.newBuilder()
                .setNamespace(temporal.getOptions().getNamespace())
                .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId))
                .build());
  }

  // --- stubs ---

  /** Every step succeeds: the Shipment is created, the Payment captured, each change made. */
  void stubEveryStep() {
    DOWNSTREAM.stubFor(post("/shipments").willReturn(shipmentCreated()));
    DOWNSTREAM.stubFor(
        readPaymentsRequest()
            .willReturn(
                okJson(
                    "[{\"id\": \"%s\", \"orderId\": \"%s\", \"status\": \"AUTHORIZED\"}]"
                        .formatted(PAYMENT_ID, orderId))));
    DOWNSTREAM.stubFor(
        post(CAPTURE)
            .willReturn(
                okJson("{\"id\": \"%s\", \"status\": \"CAPTURED\"}".formatted(PAYMENT_ID))));
    DOWNSTREAM.stubFor(
        patch(WireMock.urlPathMatching("/orders/[^/]+/status"))
            .willReturn(okJson("{\"id\": \"%s\"}".formatted(orderId))));
    DOWNSTREAM.stubFor(
        post(DISPATCH).willReturn(okJson("{\"id\": \"%s\"}".formatted(SHIPMENT_ID))));
    DOWNSTREAM.stubFor(post(HOLD).willReturn(okJson("{\"id\": \"%s\"}".formatted(SHIPMENT_ID))));
  }

  /** The Order has its Shipment already, in {@code status}: creating one again is a 409. */
  void stubShipmentExists(String status, String holdReason) {
    DOWNSTREAM.stubFor(
        post("/shipments")
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody("{\"reason\": \"shipmentExists\"}")));
    DOWNSTREAM.stubFor(
        get(urlEqualTo("/shipments?orderId=" + orderId))
            .willReturn(
                okJson(
                    """
                    {"id": "%s", "orderId": "%s", "status": "%s", "holdReason": %s}
                    """
                        .formatted(
                            SHIPMENT_ID,
                            orderId,
                            status,
                            holdReason == null ? "null" : "\"" + holdReason + "\""))));
  }

  static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder shipmentCreated() {
    return aResponse()
        .withStatus(201)
        .withHeader("Content-Type", "application/json")
        .withBody(
            """
            {"id": "%s", "status": "AWAITING_PICK"}
            """
                .formatted(SHIPMENT_ID));
  }

  /** The gateway declines the capture, as it does for {@code tok_capture_decline}. */
  static void stubCaptureDeclined() {
    DOWNSTREAM.stubFor(
        post(CAPTURE)
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(402)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody(
                        """
                        {"status": 402, "reason": "captureDeclined",
                         "declineReason": "authorization_expired"}
                        """)));
  }

  MappingBuilder readPaymentsRequest() {
    return get(urlPathEqualTo("/payments"))
        .withQueryParam("orderId", equalTo(orderId))
        .withQueryParam("customerId", equalTo(CUSTOMER_ID));
  }

  MappingBuilder statusChangeTo(String status) {
    return patch(urlEqualTo(status()))
        .withRequestBody(matchingJsonPath("$.status", equalTo(status)));
  }

  // --- what the stubs were called with ---

  String status() {
    return "/orders/" + orderId + "/status";
  }

  String readPayments() {
    return "/payments?orderId=" + orderId + "&customerId=" + CUSTOMER_ID;
  }

  /** This Order's calls, oldest first; a status change shows the status it asked for. */
  List<String> calls() {
    return ofThisOrder().stream()
        .map(
            r ->
                r.getMethod()
                    + " "
                    + r.getUrl()
                    + (r.getMethod().getName().equals("PATCH")
                        ? " " + statusIn(r.getBodyAsString())
                        : ""))
        .toList();
  }

  List<String> keys() {
    return ofThisOrder().stream().map(r -> r.getHeader("Idempotency-Key")).toList();
  }

  /** Every call made for this Order: those of a marker Order are left out. */
  List<LoggedRequest> ofThisOrder() {
    return downstreamRequests().stream()
        .filter(r -> !r.getUrl().equals("/shipments") || r.getBodyAsString().contains(orderId))
        .toList();
  }

  void awaitCalls(int count) {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(calls()).hasSizeGreaterThanOrEqualTo(count));
  }

  static long shipmentsCreatedFor(String orderId) {
    return downstreamRequests().stream()
        .filter(r -> r.getUrl().equals("/shipments") && r.getBodyAsString().contains(orderId))
        .count();
  }

  private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([A-Z_]+)\"");

  private static String statusIn(String body) {
    var matcher = STATUS.matcher(body);
    return matcher.find() ? matcher.group(1) : "?";
  }
}
