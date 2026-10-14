package com.ecomm.orchestration;

import static com.ecomm.orchestration.CheckoutWorkflowTest.downstreamRequests;
import static com.ecomm.orchestration.CheckoutWorkflowTest.stubToken;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.client.WorkflowClient;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

/**
 * The fulfillment Saga, from Kafka in to HTTP out: Orchestration's real consumers read Order and
 * Shipment events from Kafka, and its real activities call WireMock stubs of Fulfillment &
 * Shipping, Payment and Order Management with Orchestration's own token.
 *
 * <p>Time skipping is locked while each test runs, so the Saga's 30-day wait for delivery doesn't
 * end before the test's Shipment events arrive; a test that wants the wait over skips it.
 */
class FulfillmentWorkflowTest extends OrchestrationTest {

  static final String CUSTOMER_ID = "customer-42";
  static final String SHIPMENT_ID = "0b8e6f2a-1c3d-4e5f-a6b7-c8d9e0f1a2b3";
  static final String PAYMENT_ID = "9a8b7c6d-0000-4000-8000-000000000002";

  @Autowired WorkflowClient temporal;
  @Autowired KafkaListenerEndpointRegistry listeners;

  String orderId;
  String correlationId;

  @BeforeEach
  void newOrder() {
    orderId = UUID.randomUUID().toString();
    correlationId = "corr-" + orderId;
    stubToken();
    TimeSkipping.lock();
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

  @AfterEach
  void unlockTime() {
    TimeSkipping.unlock();
  }

  @Test
  void aPaidOrderWithAnAddressStartsItsSagaWhichCreatesTheShipment() {
    stubCreateShipment();

    paid();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> DOWNSTREAM.verify(postRequestedFor(urlEqualTo("/shipments"))));
    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo("/shipments"))
            .withHeader("Authorization", equalTo("Bearer orchestration-token"))
            .withHeader(
                "Idempotency-Key",
                equalTo(workflowId() + ":" + runIdOf(workflowId()) + ":createShipment"))
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
    assertThat(downstreamRequests()).hasSize(1);
  }

  // --- events ---

  /** The Order's {@code PAID} event, as Order Management publishes it once checkout paid it. */
  void paid() {
    FulfillmentEvents.order(orderId, CUSTOMER_ID, 2, "PAID", true, correlationId);
  }

  // --- the run ---

  String workflowId() {
    return "fulfillment:" + orderId;
  }

  String runIdOf(String workflowId) {
    return temporal
        .getWorkflowServiceStubs()
        .blockingStub()
        .describeWorkflowExecution(
            DescribeWorkflowExecutionRequest.newBuilder()
                .setNamespace(temporal.getOptions().getNamespace())
                .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId))
                .build())
        .getWorkflowExecutionInfo()
        .getExecution()
        .getRunId();
  }

  // --- stubs ---

  static void stubCreateShipment() {
    DOWNSTREAM.stubFor(
        post("/shipments")
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"id": "%s", "status": "AWAITING_PICK"}
                        """
                            .formatted(SHIPMENT_ID))));
  }
}
