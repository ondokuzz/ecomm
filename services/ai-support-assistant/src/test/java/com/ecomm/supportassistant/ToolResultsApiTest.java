package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.supportassistant.application.port.out.SupportModel;
import com.ecomm.supportassistant.domain.Entry;
import com.ecomm.supportassistant.domain.ToolCall;
import com.ecomm.supportassistant.domain.ToolResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * What each lookup hands the model, with a scripted provider asking for it: the context's answer
 * trimmed to what the tool's description promises, an input the tool can't take as an error result
 * that asks nothing, and another Customer's records as not found or none.
 */
@Import(GroundingApiTest.Provider.class)
class ToolResultsApiTest extends SupportAssistantApiTest {

  @Autowired ScriptedSupportModel model;

  @BeforeEach
  void resetModel() {
    model.reset();
  }

  /** The result the provider was handed for {@code tool} called with {@code input}. */
  private ToolResult resultOf(String tool, Map<String, Object> input) {
    model.script(
        new SupportModel.ToolUse(
            List.of(new ToolCall("call-1", tool, input)), SupportModel.Usage.NONE),
        new SupportModel.Reply("Done.", SupportModel.Usage.NONE));
    send(started(), "Tell me").expectStatus().isOk();
    var results = (Entry.ToolResults) model.requests().get(1).transcript().getLast();
    return results.results().getFirst();
  }

  private static Map<String, Object> eur(String amount) {
    return Map.of("amount", amount, "currency", "EUR");
  }

  /** A map that, unlike {@link Map#of}, holds the nulls a context's JSON answers. */
  private static Map<String, Object> withNulls(Object... keysAndValues) {
    var map = new HashMap<String, Object>();
    for (var i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  @Test
  void findOrdersByReferenceAnswersTheMatchingOrdersSummaries() {
    stubOrdersByReference("7F1C2A3B", order(ORDER_ID, "PAID", "PAID"));

    var result = resultOf("find_orders", Map.of("reference", "#7f1c2a3b"));

    assertThat(result.content())
        .isEqualTo(
            Map.of(
                "orders",
                List.of(
                    Map.of(
                        "orderId", ORDER_ID,
                        "reference", "#7F1C2A3B",
                        "status", "PAID",
                        "placedAt", "2026-11-03T10:15:00Z",
                        "total", eur("803.99")))));
    assertThat(result.records()).extracting(r -> r.id()).containsExactly(ORDER_ID);
  }

  @Test
  void findOrdersWithAReferenceThatIsntOneIsAnErrorResultAndAsksNothing() {
    var result = resultOf("find_orders", Map.of("reference", "RMA-3F2A9C1B"));

    assertThat(result.outcome()).isEqualTo(ToolResult.Outcome.ERROR);
    assertThat(result.content()).containsEntry("error", "invalidInput");
    ORDER_MANAGEMENT.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void getShipmentAnswersItsStatusCarrierTrackingAndDelivery() {
    stubShipment(
        ORDER_ID,
        okJson(shipment(ORDER_ID, "DELIVERED", "MC0123456789", "IN_TRANSIT", "DELIVERED")));

    var result = resultOf("get_shipment", Map.of("orderId", ORDER_ID));

    assertThat(result.outcome()).isEqualTo(ToolResult.Outcome.FOUND);
    assertThat(result.content())
        .isEqualTo(
            Map.of(
                "orderId", ORDER_ID,
                "orderReference", "#7F1C2A3B",
                "status", "DELIVERED",
                "carrier", "Mock Carrier",
                "trackingNumber", "MC0123456789",
                "trackingEvents",
                    List.of(
                        Map.of("status", "IN_TRANSIT", "at", "2026-11-04T09:00:00Z"),
                        Map.of("status", "DELIVERED", "at", "2026-11-05T09:00:00Z")),
                "deliveredAt", "2026-11-06T09:00:00Z"));
    assertThat(result.records()).extracting(r -> r.id()).containsExactly(ORDER_ID);
    FULFILLMENT_SHIPPING.verify(
        getRequestedFor(urlPathEqualTo("/shipments"))
            .withHeader("Authorization", equalTo("Bearer " + customerToken())));
  }

  @Test
  void getShipmentOfAnotherCustomersOrderIsNotFound() {
    stubShipment(OTHER_ORDER_ID, notFound());

    var result = resultOf("get_shipment", Map.of("orderId", OTHER_ORDER_ID));

    assertThat(result.outcome()).isEqualTo(ToolResult.Outcome.NOT_FOUND);
    assertThat(result.records()).isEmpty();
  }

  @Test
  void getPaymentsAnswersEachPaymentWithItsTransactions() {
    stubPayments(
        ORDER_ID,
        payment(
            "pay-1",
            ORDER_ID,
            "PARTIALLY_REFUNDED",
            transaction("AUTHORIZATION", "APPROVED", 3),
            transaction("CAPTURE", "APPROVED", 4),
            transaction("REFUND", 12900, "APPROVED", null, 6, "RMA-3F2A9C1B")));

    var result = resultOf("get_payments", Map.of("orderId", ORDER_ID));

    assertThat(result.content())
        .isEqualTo(
            Map.of(
                "orderId",
                ORDER_ID,
                "orderReference",
                "#7F1C2A3B",
                "payments",
                List.of(
                    withNulls(
                        "paymentId",
                        "pay-1",
                        "status",
                        "PARTIALLY_REFUNDED",
                        "amount",
                        eur("803.99"),
                        "declineReason",
                        null,
                        "transactions",
                        List.of(
                            withNulls(
                                "kind",
                                "AUTHORIZATION",
                                "amount",
                                eur("803.99"),
                                "outcome",
                                "APPROVED",
                                "declineReason",
                                null,
                                "at",
                                "2026-11-03T10:15:00Z",
                                "reference",
                                null),
                            withNulls(
                                "kind",
                                "CAPTURE",
                                "amount",
                                eur("803.99"),
                                "outcome",
                                "APPROVED",
                                "declineReason",
                                null,
                                "at",
                                "2026-11-04T10:15:00Z",
                                "reference",
                                null),
                            withNulls(
                                "kind", "REFUND",
                                "amount", eur("129.00"),
                                "outcome", "APPROVED",
                                "declineReason", null,
                                "at", "2026-11-06T10:15:00Z",
                                "reference", "RMA-3F2A9C1B"))))));
    assertThat(result.records()).extracting(r -> r.id()).containsExactly(ORDER_ID);
  }

  @Test
  void getPaymentsOfAnotherCustomersOrderAnswersNoneAndNoRecord() {
    stubPayments(OTHER_ORDER_ID);

    var result = resultOf("get_payments", Map.of("orderId", OTHER_ORDER_ID));

    assertThat(result.content()).containsEntry("payments", List.of());
    assertThat(result.records()).isEmpty();
  }

  @Test
  void theHistoryAnswersEachLineWithItsPaymentAndOrder() {
    stubHistory(
        historyLine(
            transaction("AUTHORIZATION", 80399, "DECLINED", "card_declined", 3, null),
            ORDER_ID,
            "DECLINED"));

    var result = resultOf("get_payment_transaction_history", Map.of());

    assertThat(result.content())
        .isEqualTo(
            Map.of(
                "transactions",
                List.of(
                    withNulls(
                        "kind", "AUTHORIZATION",
                        "amount", eur("803.99"),
                        "outcome", "DECLINED",
                        "declineReason", "card_declined",
                        "at", "2026-11-03T10:15:00Z",
                        "reference", null,
                        "paymentId", "pay-7f1c2a3b",
                        "paymentStatus", "DECLINED",
                        "orderId", ORDER_ID,
                        "orderReference", "#7F1C2A3B")),
                "total",
                1L));
    assertThat(result.records()).extracting(r -> r.id()).containsExactly(ORDER_ID);
  }

  @Test
  void theHistoryIsNarrowedToTheRangeAsked() {
    stubHistory();

    resultOf(
        "get_payment_transaction_history",
        Map.of("from", "2026-11-01T00:00:00Z", "to", "2026-11-08T00:00:00Z"));

    PAYMENT.verify(
        getRequestedFor(urlPathEqualTo("/payments/transactions"))
            .withQueryParam("from", equalTo("2026-11-01T00:00:00Z"))
            .withQueryParam("to", equalTo("2026-11-08T00:00:00Z"))
            .withHeader("Authorization", equalTo("Bearer " + customerToken())));
  }

  @Test
  void aRangeThatIsntInstantsIsAnErrorResultAndAsksNothing() {
    var result = resultOf("get_payment_transaction_history", Map.of("from", "last week"));

    assertThat(result.outcome()).isEqualTo(ToolResult.Outcome.ERROR);
    assertThat(result.content()).containsEntry("error", "invalidInput");
    PAYMENT.verify(0, anyRequestedFor(anyUrl()));
  }
}
