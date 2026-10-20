package com.ecomm.orchestration;

import static com.ecomm.orchestration.CheckoutWorkflowTest.downstreamRequests;
import static com.ecomm.orchestration.CheckoutWorkflowTest.stubToken;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.orchestration.application.port.in.ReturnsOutcome;
import com.ecomm.orchestration.application.port.in.ReturnsOutcome.Status;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.grpc.StatusRuntimeException;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.testservice.v1.LockTimeSkippingRequest;
import io.temporal.api.testservice.v1.UnlockTimeSkippingRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowException;
import io.temporal.serviceclient.TestServiceStubs;
import io.temporal.serviceclient.TestServiceStubsOptions;
import io.temporal.testserver.TestServer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * The returns Saga, from Kafka in to HTTP out: Orchestration's real consumer reads RMA events from
 * Kafka, and its real activities call WireMock stubs of Returns & Warranty, Inventory, Payment,
 * Fulfillment & Shipping and Order Management with Orchestration's own token.
 *
 * <p>It runs on a test server of its own, with time skipping on: whenever the Saga only waits, on
 * its Send-back timer, an Inspection check or a retry's backoff, the server's clock jumps ahead.
 * Its own, because runs earlier classes left on the shared server, taken up by this context's
 * checkout and fulfillment workers, kept time from jumping here. So a run awaiting its units is
 * expired at once unless the RMA it reads is further along already, and a test that must signal it
 * first holds time still meanwhile. Each run left open is cancelled, not terminated, when its test
 * ends: a cancelled run ends through a workflow task of its own, which leaves time skipping as it
 * was.
 */
// This class's Sagas run on a time-skipping test server of their own.
@TestPropertySource(
    properties = "spring.temporal.connection.target=localhost:${ecomm.test.returns-port}")
class ReturnsWorkflowTest extends OrchestrationTest {

  static final int RETURNS_PORT = freePort();

  static {
    TestServer.createPortBoundServer(RETURNS_PORT, false);
  }

  @DynamicPropertySource
  static void returnsServer(DynamicPropertyRegistry registry) {
    registry.add("ecomm.test.returns-port", () -> RETURNS_PORT);
  }

  static final String CUSTOMER_ID = "customer-42";
  static final String VARIANT_ID = "PHN-PIXEL-9";

  @Autowired WorkflowClient temporal;
  @Autowired KafkaListenerEndpointRegistry listeners;

  String rmaId;
  String orderId;
  String reference;
  String correlationId;

  /** Every RMA whose APPROVED event this test sent: their Sagas end with the test. */
  final List<String> rmasApproved = new ArrayList<>();

  @BeforeEach
  void newRma() {
    rmaId = UUID.randomUUID().toString();
    orderId = UUID.randomUUID().toString();
    reference = "RMA-" + rmaId.substring(0, 8).toUpperCase();
    correlationId = "corr-" + rmaId;
    stubToken();
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

  /** Whether this test holds time still: it lets it go once its runs have ended. */
  boolean timeHeld;

  /** Cancels every run the test left open, and waits until it has ended. */
  @AfterEach
  void endTheRuns() {
    for (var id : rmasApproved) {
      var workflowId = "returns:" + id;
      try {
        temporal.newUntypedWorkflowStub(workflowId).cancel();
      } catch (WorkflowException | StatusRuntimeException e) {
        // It ended, or never started.
      }
      await()
          .atMost(Duration.ofSeconds(30))
          .until(() -> !isStarted(workflowId) || !isRunning(workflowId));
    }
    if (timeHeld) {
      var server = testServer();
      try {
        server.blockingStub().unlockTimeSkipping(UnlockTimeSkippingRequest.getDefaultInstance());
      } finally {
        server.shutdownNow();
      }
    }
  }

  @Test
  void anApprovedRmaWhoseUnitsNeverComeIsExpiredOnceItsSendBackDeadlinePasses() {
    var sendBackBy = serverNow().plus(Duration.ofDays(14));
    stubRma("APPROVED", sendBackBy, null, null);
    stubExpire(200);

    approved(sendBackBy);

    assertThat(outcome()).isEqualTo(new ReturnsOutcome(Status.EXPIRED, null, null, null));
    assertThat(calls()).containsExactly("GET " + rma(), "POST " + rma() + "/expire");
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(rma() + "/expire"))
            .withHeader("Authorization", equalTo("Bearer orchestration-token"))
            .withHeader("Idempotency-Key", equalTo(keyOf("expireRma")))
            .withHeader("X-Correlation-Id", equalTo(correlationId)));
  }

  @Test
  void unitsReceivedJustBeforeTheTimerAreInspectedNotExpired() {
    holdTimeStill();
    var sendBackBy = serverNow().plus(Duration.ofDays(14));
    stubInSequence(
        () -> get(urlEqualTo(rma())),
        okJson(rmaJson("APPROVED", sendBackBy, null, null)),
        okJson(rmaJson("INSPECTED", sendBackBy, "REFUSED", "Water damage")));
    stubExpire(200);
    stubResolve(false);
    approved(sendBackBy);
    awaitCalls(1);

    rmaEvent(3, "RECEIVED");
    awaitSignals(1);
    // Time jumps past the Send-back deadline, and the Inspection checks find it inspected.
    letTimeRun();

    assertThat(outcome()).isEqualTo(new ReturnsOutcome(Status.CLOSED, null, null, null));
    assertThat(calls())
        .doesNotContain("POST " + rma() + "/expire")
        .endsWith("GET " + rma(), "POST " + rma() + "/resolve");
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(rma() + "/resolve"))
            .withHeader("Idempotency-Key", equalTo(keyOf("resolveRma")))
            .withRequestBody(
                equalToJson("{\"resolution\": \"CLOSED\", \"note\": \"Water damage\"}")));
  }

  /**
   * The units were received as the timer fired, and their RECEIVED and INSPECTED events were lost:
   * expiring is refused (409), so the Saga awaits the Inspection, and the read it makes once an
   * Inspection check passes finds it.
   */
  @Test
  void anExpiryRefusedAsReceivedGoesOnAndTheHourlyReadFindsALostInspection() {
    var sendBackBy = serverNow().plus(Duration.ofDays(14));
    stubInSequence(
        () -> get(urlEqualTo(rma())),
        okJson(rmaJson("APPROVED", sendBackBy, null, null)),
        okJson(rmaJson("INSPECTED", sendBackBy, "NO_FAULT_FOUND", "Works as designed")));
    stubExpire(409);
    stubResolve(false);

    approved(sendBackBy);

    assertThat(outcome()).isEqualTo(new ReturnsOutcome(Status.CLOSED, null, null, null));
    assertThat(calls())
        .containsExactly(
            "GET " + rma(),
            "POST " + rma() + "/expire",
            "GET " + rma(),
            "GET " + rma(),
            "POST " + rma() + "/resolve");
  }

  @Test
  void anAcceptedReturnIsRestockedRefundedAndResolvedThenItsOrderReturned() {
    stubEveryStep();
    stubResolve(true);

    throughInspection("ACCEPTED", null);

    assertThat(outcome())
        .isEqualTo(new ReturnsOutcome(Status.REFUNDED, "mock-refund-1", null, null));
    assertThat(calls())
        .containsExactly(
            "GET " + rma(),
            "GET " + rma(),
            "POST /stock/" + VARIANT_ID + "/restocks",
            "GET " + readPayments(),
            "POST " + REFUNDS,
            "POST " + rma() + "/resolve",
            "PATCH /orders/" + orderId + "/status");
    assertThat(keys())
        .containsExactly(
            null,
            null,
            keyOf("restock"),
            null,
            keyOf("refundPayment"),
            keyOf("resolveRma"),
            keyOf("markOrderReturned"));
    assertThat(downstreamRequests())
        .allSatisfy(r -> assertThat(r.getHeader("X-Correlation-Id")).isEqualTo(correlationId));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/stock/" + VARIANT_ID + "/restocks"))
            .withRequestBody(
                equalToJson("{\"quantity\": 2, \"reference\": \"%s\"}".formatted(reference))));
    // The Refund amount is the whole RMA's: refunded as it is, never times its quantity.
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(REFUNDS))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "customer-42", "reference": "%s",
                     "amount": {"amountMinor": 59800, "currency": "EUR"}}
                    """
                        .formatted(reference))));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(rma() + "/resolve"))
            .withRequestBody(
                equalToJson(
                    "{\"resolution\": \"REFUNDED\", \"paymentTransactionReference\": \"mock-refund-1\"}")));
    DOWNSTREAM.verify(
        WireMock.patchRequestedFor(urlEqualTo("/orders/" + orderId + "/status"))
            .withRequestBody(
                equalToJson("{\"customerId\": \"customer-42\", \"status\": \"RETURNED\"}")));
  }

  @Test
  void aRefundThatLeavesOtherUnitsUnrefundedLeavesTheOrderAsItIs() {
    stubEveryStep();
    stubResolve(false);

    throughInspection("ACCEPTED", null);

    assertThat(outcome().status()).isEqualTo(Status.REFUNDED);
    assertThat(calls()).noneMatch(c -> c.startsWith("PATCH"));
  }

  @Test
  void aConfirmedFaultIsReplacedReservedCommittedShippedAndDispatchedOncePacked() {
    stubEveryStep();
    stubResolve(false);
    throughInspection("FAULT_CONFIRMED", "Charging port worn");
    awaitCalls(6);

    FulfillmentEvents.replacement(REPLACEMENT_ID, rmaId, orderId, CUSTOMER_ID, 2, "PICKED");
    FulfillmentEvents.replacement(REPLACEMENT_ID, rmaId, orderId, CUSTOMER_ID, 3, "PACKED");

    assertThat(outcome())
        .isEqualTo(new ReturnsOutcome(Status.EXCHANGED, null, REPLACEMENT_ID, null));
    assertThat(calls())
        .containsExactly(
            "GET " + rma(),
            "GET " + rma(),
            "POST /reservations",
            "POST /reservations/" + RESERVATION_ID + "/commit",
            "GET /shipments?orderId=" + orderId,
            "POST /shipments",
            "POST /shipments/" + REPLACEMENT_ID + "/dispatch",
            "POST " + rma() + "/resolve");
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/reservations"))
            .withRequestBody(
                equalToJson(
                    """
                    {"customerId": "rma:%s", "items": [{"variantId": "%s", "quantity": 2}]}
                    """
                        .formatted(rmaId, VARIANT_ID),
                    true,
                    true)));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/reservations/" + RESERVATION_ID + "/commit"))
            .withRequestBody(equalToJson("{\"customerId\": \"rma:%s\"}".formatted(rmaId))));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/shipments"))
            .withHeader("Idempotency-Key", equalTo(keyOf("createReplacement")))
            .withRequestBody(
                equalToJson(
                    """
                    {"kind": "REPLACEMENT", "rmaId": "%s", "orderId": "%s",
                     "customerId": "customer-42",
                     "lines": [{"variantId": "%s", "quantity": 2}],
                     "shippingAddress": {"recipientName": "Erika Mustermann",
                                         "line1": "Hauptstraße 5", "postcode": "10115",
                                         "city": "Berlin", "country": "DE"},
                     "shippingOption": {"id": "EXPRESS", "carrier": "Mock Carrier",
                                        "cost": {"amountMinor": 999, "currency": "EUR"}}}
                    """
                        .formatted(rmaId, orderId, VARIANT_ID))));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/shipments/" + REPLACEMENT_ID + "/dispatch"))
            .withHeader("Idempotency-Key", equalTo(keyOf("dispatchReplacement"))));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(rma() + "/resolve"))
            .withRequestBody(
                equalToJson(
                    "{\"resolution\": \"EXCHANGED\", \"replacementShipmentId\": \"%s\"}"
                        .formatted(REPLACEMENT_ID))));
  }

  /** A second replacement for the RMA is a 409: the one it has is read by RMA, never by Order. */
  @Test
  void aReplacementThatExistsAlreadyIsReadByRma() {
    stubEveryStep();
    stubResolve(false);
    DOWNSTREAM.stubFor(
        post("/shipments")
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody("{\"reason\": \"shipmentExists\"}")));
    DOWNSTREAM.stubFor(
        get(urlEqualTo("/shipments?rmaId=" + rmaId))
            .willReturn(
                okJson("{\"id\": \"%s\", \"status\": \"SHIPPED\"}".formatted(REPLACEMENT_ID))));

    throughInspection("FAULT_CONFIRMED", null);

    assertThat(outcome())
        .isEqualTo(new ReturnsOutcome(Status.EXCHANGED, null, REPLACEMENT_ID, null));
    assertThat(calls())
        .contains("GET /shipments?rmaId=" + rmaId)
        .doesNotContain("POST /shipments/" + REPLACEMENT_ID + "/dispatch");
  }

  @Test
  void aConfirmedFaultWithNoStockIsRefundedWithoutRestocking() {
    stubEveryStep();
    stubResolve(false);
    DOWNSTREAM.stubFor(
        post("/reservations")
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody(
                        "{\"status\": 409, \"insufficientStock\": [\"%s\"]}"
                            .formatted(VARIANT_ID))));

    throughInspection("FAULT_CONFIRMED", "Charging port worn");

    assertThat(outcome())
        .isEqualTo(new ReturnsOutcome(Status.REFUNDED, "mock-refund-1", null, null));
    assertThat(calls())
        .containsExactly(
            "GET " + rma(),
            "GET " + rma(),
            "POST /reservations",
            "GET " + readPayments(),
            "POST " + REFUNDS,
            "POST " + rma() + "/resolve");
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(rma() + "/resolve"))
            .withRequestBody(
                equalToJson(
                    """
                    {"resolution": "REFUNDED", "paymentTransactionReference": "mock-refund-1",
                     "note": "No replacement in Stock"}
                    """)));
  }

  @Test
  void aDeclinedRefundHoldsTheRmaAndKeepsTheRestock() {
    stubEveryStep();
    DOWNSTREAM.stubFor(
        post(REFUNDS)
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(402)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody(
                        """
                        {"status": 402, "reason": "refundDeclined",
                         "declineReason": "refund_window_closed"}
                        """)));

    throughInspection("ACCEPTED", null);

    assertThat(outcome())
        .isEqualTo(
            new ReturnsOutcome(
                Status.REFUND_REFUSED, null, null, "Refund declined: refund_window_closed"));
    assertThat(calls())
        .containsExactly(
            "GET " + rma(),
            "GET " + rma(),
            "POST /stock/" + VARIANT_ID + "/restocks",
            "GET " + readPayments(),
            "POST " + REFUNDS,
            "POST " + rma() + "/hold");
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(rma() + "/hold"))
            .withHeader("Idempotency-Key", equalTo(keyOf("holdRma")))
            .withRequestBody(
                equalToJson("{\"reason\": \"Refund declined: refund_window_closed\"}")));
  }

  /** A Refund still failing after its minute of retries, time skipped, holds the RMA. */
  @Test
  void aRefundThatKeepsFailingHoldsTheRma() {
    stubEveryStep();
    DOWNSTREAM.stubFor(post(REFUNDS).atPriority(1).willReturn(aResponse().withStatus(502)));
    var sendBackBy = serverNow().plus(Duration.ofDays(14));
    stubRma("INSPECTED", sendBackBy, "ACCEPTED", null);

    approved(sendBackBy);

    assertThat(outcome())
        .isEqualTo(
            new ReturnsOutcome(
                Status.REFUND_REFUSED,
                null,
                null,
                "Refund failed: the Payment couldn't be refunded"));
    assertThat(calls()).filteredOn(c -> c.equals("POST " + REFUNDS)).hasSizeGreaterThan(1);
    assertThat(calls()).endsWith("GET " + readPayments(), "POST " + rma() + "/hold");
  }

  @Test
  void aRestockAnswered503IsRetriedWithTheSameKeyUntilItSucceeds() {
    stubEveryStep();
    stubResolve(false);
    CheckoutWorkflowTest.stubInSequence(
        () -> post(urlEqualTo("/stock/" + VARIANT_ID + "/restocks")),
        aResponse().withStatus(503),
        okJson("{\"variantId\": \"%s\"}".formatted(VARIANT_ID)));

    throughInspection("ACCEPTED", null);

    assertThat(outcome().status()).isEqualTo(Status.REFUNDED);
    var restocks =
        downstreamRequests().stream()
            .filter(r -> r.getUrl().equals("/stock/" + VARIANT_ID + "/restocks"))
            .map(r -> r.getHeader("Idempotency-Key"))
            .toList();
    assertThat(restocks).containsExactly(keyOf("restock"), keyOf("restock"));
  }

  @Test
  void theSameApprovalTwiceAndAReplayAfterTheSagaClosedStartNothingMore() {
    var sendBackBy = serverNow().plus(Duration.ofDays(14));
    stubRma("EXPIRED", sendBackBy, null, null);
    approved(sendBackBy);
    approved(sendBackBy);
    assertThat(outcome()).isEqualTo(new ReturnsOutcome(Status.EXPIRED, null, null, null));
    var firstRun = runIdOf(workflowId());

    approved(sendBackBy);
    var marker = anotherApproval();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(isStarted("returns:" + marker)).isTrue());
    assertThat(runIdOf(workflowId())).isEqualTo(firstRun);
    assertThat(calls()).filteredOn(c -> c.equals("GET " + rma())).hasSize(1);
  }

  /**
   * An APPROVED event replayed once Temporal no longer keeps the first run starts a second, which
   * finds the RMA resolved: it ends as the first did, and changes nothing.
   */
  @Test
  void aRunThatFindsItsRmaResolvedAlreadyEndsAsTheFirstDid() {
    var sendBackBy = serverNow().plus(Duration.ofDays(14));
    stubRma("ON_HOLD", sendBackBy, "ACCEPTED", null);

    approved(sendBackBy);

    assertThat(outcome().status()).isEqualTo(Status.REFUND_REFUSED);
    assertThat(calls()).containsExactly("GET " + rma());
  }

  @Test
  void duplicateAndStaleRmaEventsAreIgnored() {
    stubEveryStep();
    stubResolve(false);
    holdTimeStill();
    var sendBackBy = serverNow().plus(Duration.ofDays(14));
    stubInSequence(
        () -> get(urlEqualTo(rma())),
        okJson(rmaJson("APPROVED", sendBackBy, null, null)),
        okJson(rmaJson("INSPECTED", sendBackBy, "ACCEPTED", null)));
    approved(sendBackBy);
    awaitCalls(1);

    rmaEvent(4, "INSPECTED");
    rmaEvent(4, "INSPECTED");
    rmaEvent(3, "RECEIVED");

    assertThat(outcome().status()).isEqualTo(Status.REFUNDED);
    assertThat(calls()).filteredOn(c -> c.equals("POST " + REFUNDS)).hasSize(1);
  }

  // --- events ---

  /** The RMA's APPROVED event, as Returns & Warranty publishes it once Staff approved it. */
  void approved(Instant sendBackBy) {
    rmasApproved.add(rmaId);
    RmaEvents.send(rmaId, 2, "APPROVED", "APPROVED", rmaOf("RETURN", sendBackBy), correlationId);
  }

  /** Another RMA, approved and already expired: once its Saga starts, every event before has. */
  String anotherApproval() {
    var other = UUID.randomUUID().toString();
    rmasApproved.add(other);
    DOWNSTREAM.stubFor(
        get(urlEqualTo("/rmas/" + other))
            .willReturn(okJson("{\"id\": \"%s\", \"status\": \"EXPIRED\"}".formatted(other))));
    RmaEvents.send(other, 2, "APPROVED", "APPROVED", rmaOf("RETURN", null), "corr-" + other);
    return other;
  }

  /** A later event of the RMA, at {@code version}, in {@code status}. */
  void rmaEvent(long version, String status) {
    RmaEvents.send(
        rmaId,
        version,
        status,
        status,
        rmaOf("RETURN", Instant.now().plus(Duration.ofDays(14))),
        correlationId);
  }

  /**
   * Takes the RMA, with time held still, from its approval through receipt to its Inspection with
   * {@code outcome}, each by its event; the RMA reads APPROVED at first, then INSPECTED.
   */
  void throughInspection(String outcome, String note) {
    holdTimeStill();
    var sendBackBy = serverNow().plus(Duration.ofDays(14));
    stubInSequence(
        () -> get(urlEqualTo(rma())),
        okJson(rmaJson("APPROVED", sendBackBy, null, null)),
        okJson(rmaJson("INSPECTED", sendBackBy, outcome, note)));
    approved(sendBackBy);
    awaitCalls(1);
    rmaEvent(3, "RECEIVED");
    rmaEvent(4, "INSPECTED");
  }

  RmaEvents.Rma rmaOf(String kind, Instant sendBackBy) {
    return new RmaEvents.Rma(
        reference, orderId, CUSTOMER_ID, VARIANT_ID, 2, kind, 59800L, sendBackBy);
  }

  // --- stubs ---

  static final String PAYMENT_ID = "9a8b7c6d-0000-4000-8000-000000000002";
  static final String REFUNDS = "/payments/" + PAYMENT_ID + "/refunds";
  static final String RESERVATION_ID = "5e1d2c3b-0000-4000-8000-000000000003";
  static final String REPLACEMENT_ID = "0b8e6f2a-0000-4000-8000-000000000004";

  /** Every step but resolving succeeds: restock, Payment, Refund, Reservation, replacement. */
  void stubEveryStep() {
    DOWNSTREAM.stubFor(
        post("/stock/" + VARIANT_ID + "/restocks")
            .willReturn(okJson("{\"variantId\": \"%s\"}".formatted(VARIANT_ID))));
    DOWNSTREAM.stubFor(
        get(urlPathEqualTo("/payments/by-order"))
            .withQueryParam("orderId", equalTo(orderId))
            .withQueryParam("customerId", equalTo(CUSTOMER_ID))
            .willReturn(
                okJson(
                    """
                    [{"id": "%s", "orderId": "%s", "status": "CAPTURED",
                      "transactions": [{"kind": "CAPTURE", "outcome": "APPROVED",
                                        "gatewayReference": "mock-capture", "reference": null}]}]
                    """
                        .formatted(PAYMENT_ID, orderId))));
    DOWNSTREAM.stubFor(
        post(REFUNDS)
            .willReturn(
                okJson(
                    """
                    {"id": "%s", "status": "PARTIALLY_REFUNDED",
                     "transactions": [{"kind": "CAPTURE", "outcome": "APPROVED",
                                       "gatewayReference": "mock-capture", "reference": null},
                                      {"kind": "REFUND", "outcome": "APPROVED",
                                       "gatewayReference": "mock-refund-1", "reference": "%s"}]}
                    """
                        .formatted(PAYMENT_ID, reference))));
    DOWNSTREAM.stubFor(
        WireMock.patch(urlEqualTo("/orders/" + orderId + "/status"))
            .willReturn(okJson("{\"id\": \"%s\", \"status\": \"RETURNED\"}".formatted(orderId))));
    DOWNSTREAM.stubFor(
        post("/reservations")
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\": \"%s\", \"status\": \"ACTIVE\"}".formatted(RESERVATION_ID))));
    DOWNSTREAM.stubFor(
        post("/reservations/" + RESERVATION_ID + "/commit")
            .willReturn(
                okJson("{\"id\": \"%s\", \"status\": \"COMMITTED\"}".formatted(RESERVATION_ID))));
    DOWNSTREAM.stubFor(
        get(urlEqualTo("/shipments?orderId=" + orderId))
            .willReturn(
                okJson(
                    """
                    {"id": "order-shipment", "orderId": "%s", "status": "DELIVERED",
                     "shippingAddress": {"recipientName": "Erika Mustermann",
                                         "line1": "Hauptstraße 5", "line2": null,
                                         "postcode": "10115", "city": "Berlin", "region": null,
                                         "country": "DE", "phone": null},
                     "shippingOption": {"id": "EXPRESS", "carrier": "Mock Carrier",
                                        "cost": {"amountMinor": 999, "currency": "EUR"},
                                        "estimatedDays": 1}}
                    """
                        .formatted(orderId))));
    DOWNSTREAM.stubFor(
        post("/shipments")
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\": \"%s\", \"kind\": \"REPLACEMENT\", \"status\": \"AWAITING_PICK\"}"
                            .formatted(REPLACEMENT_ID))));
    DOWNSTREAM.stubFor(
        post("/shipments/" + REPLACEMENT_ID + "/dispatch")
            .willReturn(
                okJson("{\"id\": \"%s\", \"status\": \"SHIPPED\"}".formatted(REPLACEMENT_ID))));
    DOWNSTREAM.stubFor(
        post(rma() + "/hold")
            .willReturn(okJson("{\"id\": \"%s\", \"status\": \"ON_HOLD\"}".formatted(rmaId))));
  }

  /** Resolving answers whether the Order's every delivered unit is now refunded. */
  void stubResolve(boolean orderFullyRefunded) {
    DOWNSTREAM.stubFor(
        post(rma() + "/resolve")
            .willReturn(
                okJson(
                    "{\"id\": \"%s\", \"status\": \"RESOLVED\", \"orderFullyRefunded\": %s}"
                        .formatted(rmaId, orderFullyRefunded))));
  }

  static void stubInSequence(
      java.util.function.Supplier<com.github.tomakehurst.wiremock.client.MappingBuilder> request,
      com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder first,
      com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder then) {
    CheckoutWorkflowTest.stubInSequence(request, first, then);
  }

  /**
   * Returns & Warranty's read of the RMA: in {@code status}, with an Inspection when {@code
   * outcome} isn't null.
   */
  void stubRma(String status, Instant sendBackBy, String outcome, String note) {
    DOWNSTREAM.stubFor(
        get(urlEqualTo(rma())).willReturn(okJson(rmaJson(status, sendBackBy, outcome, note))));
  }

  String rmaJson(String status, Instant sendBackBy, String outcome, String note) {
    var inspection =
        outcome == null
            ? "null"
            : "{\"outcome\": \"%s\", \"note\": %s}"
                .formatted(outcome, note == null ? "null" : "\"" + note + "\"");
    return """
        {"id": "%s", "reference": "%s", "orderId": "%s", "customerId": "%s",
         "variantId": "%s", "quantity": 2, "kind": "%s",
         "refundAmount": {"amountMinor": 59800, "currency": "EUR"},
         "status": "%s", "sendBackBy": "%s", "warehouseId": "WH-BER", "inspection": %s}
        """
        .formatted(
            rmaId,
            reference,
            orderId,
            CUSTOMER_ID,
            VARIANT_ID,
            outcome != null && outcome.contains("FAULT") ? "WARRANTY_CLAIM" : "RETURN",
            status,
            sendBackBy,
            inspection);
  }

  void stubExpire(int status) {
    DOWNSTREAM.stubFor(
        post(rma() + "/expire")
            .willReturn(
                status == 200
                    ? okJson("{\"id\": \"%s\", \"status\": \"EXPIRED\"}".formatted(rmaId))
                    : aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody(
                            "{\"status\": %d, \"reason\": \"transitionRefused\"}"
                                .formatted(status))));
  }

  // --- the run ---

  String rma() {
    return "/rmas/" + rmaId;
  }

  String workflowId() {
    return "returns:" + rmaId;
  }

  ReturnsOutcome outcome() {
    await().atMost(Duration.ofSeconds(30)).until(() -> isStarted(workflowId()));
    try {
      return temporal
          .newUntypedWorkflowStub(workflowId())
          .getResult(60, TimeUnit.SECONDS, ReturnsOutcome.class);
    } catch (TimeoutException e) {
      throw new AssertionError("The Saga hadn't ended after 60 seconds", e);
    }
  }

  /** Holds the test server's time still until the test ends: only real time passes. */
  void holdTimeStill() {
    var server = testServer();
    try {
      server.blockingStub().lockTimeSkipping(LockTimeSkippingRequest.getDefaultInstance());
      timeHeld = true;
    } finally {
      server.shutdownNow();
    }
  }

  /** Lets the test server's time jump ahead again. */
  void letTimeRun() {
    var server = testServer();
    try {
      server.blockingStub().unlockTimeSkipping(UnlockTimeSkippingRequest.getDefaultInstance());
      timeHeld = false;
    } finally {
      server.shutdownNow();
    }
  }

  /** Awaits the run's having been signalled {@code count} times. */
  void awaitSignals(int count) {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        temporal.fetchHistory(workflowId()).getEvents().stream()
                            .filter(HistoryEvent::hasWorkflowExecutionSignaledEventAttributes)
                            .count())
                    .isGreaterThanOrEqualTo(count));
  }

  String runIdOf(String workflowId) {
    return describe(workflowId).getWorkflowExecutionInfo().getExecution().getRunId();
  }

  String keyOf(String step) {
    return workflowId()
        + ":"
        + describe(workflowId()).getWorkflowExecutionInfo().getExecution().getRunId()
        + ":"
        + step;
  }

  boolean isStarted(String workflowId) {
    try {
      describe(workflowId);
      return true;
    } catch (StatusRuntimeException e) {
      return false;
    }
  }

  boolean isRunning(String workflowId) {
    return describe(workflowId).getWorkflowExecutionInfo().getStatus()
        == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING;
  }

  DescribeWorkflowExecutionResponse describe(String workflowId) {
    return temporal
        .getWorkflowServiceStubs()
        .blockingStub()
        .describeWorkflowExecution(
            DescribeWorkflowExecutionRequest.newBuilder()
                .setNamespace(temporal.getOptions().getNamespace())
                .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId))
                .build());
  }

  /** The test server's clock, which time skipping has taken ahead of the wall clock. */
  static Instant serverNow() {
    var server = testServer();
    try {
      var time =
          server
              .blockingStub()
              .getCurrentTime(com.google.protobuf.Empty.getDefaultInstance())
              .getTime();
      return Instant.ofEpochSecond(time.getSeconds(), time.getNanos());
    } finally {
      server.shutdownNow();
    }
  }

  static TestServiceStubs testServer() {
    return TestServiceStubs.newServiceStubs(
        TestServiceStubsOptions.newBuilder()
            .setTarget("localhost:" + RETURNS_PORT)
            .validateAndBuildWithDefaults());
  }

  // --- what the stubs were called with ---

  /** This test's calls, oldest first. */
  List<String> calls() {
    return downstreamRequests().stream().map(r -> r.getMethod() + " " + r.getUrl()).toList();
  }

  String readPayments() {
    return "/payments/by-order?orderId=" + orderId + "&customerId=" + CUSTOMER_ID;
  }

  void awaitCalls(int count) {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(calls()).hasSizeGreaterThanOrEqualTo(count));
  }

  List<String> keys() {
    return downstreamRequests().stream().map(r -> r.getHeader("Idempotency-Key")).toList();
  }
}
