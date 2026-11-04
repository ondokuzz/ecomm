package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Asked about a charge or a Refund, the mock provider reads the Payment transactions behind it from
 * Payment, with the Customer's own token: an Order's Payments when the message names it by its
 * Order reference, or else the Customer's Payment transaction history. It explains that an
 * Authorization is a hold and the Capture the charge, that a declined one took nothing, and gives a
 * Refund's amount and what it was for.
 */
class PaymentsApiTest extends SupportAssistantApiTest {

  private void stubOrderByReference() {
    var order = order(ORDER_ID, "SHIPPED", "PLACED", "PAID", "FULFILLED", "SHIPPED");
    stubOrdersByReference("7F1C2A3B", order);
    stubOrder(ORDER_ID, okJson(order));
  }

  @Test
  void anOrdersAuthorizationAndCaptureAreOneCharge() {
    stubOrderByReference();
    stubPayments(
        ORDER_ID,
        payment(
            "pay-1",
            ORDER_ID,
            "CAPTURED",
            transaction("AUTHORIZATION", "APPROVED", 3),
            transaction("CAPTURE", "APPROVED", 4)));
    var id = started();

    send(id, "Why was I charged twice for #7F1C2A3B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "You were charged once. On Order #7F1C2A3B, the Authorization of 803.99 EUR on 3"
                + " November 2026 was only a hold on your card, and the Capture of 803.99 EUR on 4"
                + " November 2026 was the charge.")
        .jsonPath("$.references[0].id")
        .isEqualTo(ORDER_ID);

    PAYMENT.verify(
        getRequestedFor(urlPathEqualTo("/payments"))
            .withQueryParam("orderId", equalTo(ORDER_ID))
            .withHeader("Authorization", equalTo("Bearer " + customerToken()))
            .withHeader("X-Correlation-Id", equalTo("test-correlation-1")));
  }

  @Test
  void twoOrdersCapturesAreChargesForTwoOrders() {
    stubHistory(
        historyLine(transaction("CAPTURE", "APPROVED", 5), OTHER_ORDER_ID, "CAPTURED"),
        historyLine(transaction("AUTHORIZATION", "APPROVED", 4), OTHER_ORDER_ID, "CAPTURED"),
        historyLine(transaction("CAPTURE", "APPROVED", 4), ORDER_ID, "CAPTURED"),
        historyLine(transaction("AUTHORIZATION", "APPROVED", 3), ORDER_ID, "CAPTURED"));
    var id = started();

    send(id, "Why was I charged twice?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "You were charged for 2 Orders, #7F1C2A3B and #E5D40C11. On Order #7F1C2A3B, the"
                + " Authorization of 803.99 EUR on 3 November 2026 was only a hold on your card,"
                + " and the Capture of 803.99 EUR on 4 November 2026 was the charge. On Order"
                + " #E5D40C11, the Authorization of 803.99 EUR on 4 November 2026 was only a hold"
                + " on your card, and the Capture of 803.99 EUR on 5 November 2026 was the"
                + " charge.")
        .jsonPath("$.references[*].id")
        .isEqualTo(List.of(OTHER_ORDER_ID, ORDER_ID));

    PAYMENT.verify(
        getRequestedFor(urlPathEqualTo("/payments/transactions"))
            .withQueryParam("page", equalTo("0"))
            .withQueryParam("size", equalTo("20"))
            .withHeader("Authorization", equalTo("Bearer " + customerToken()))
            .withHeader("X-Correlation-Id", equalTo("test-correlation-1")));
  }

  @Test
  void aDeclinedAuthorizationTookNothingAndAnAuthorizationAloneIsAHold() {
    stubHistory(
        historyLine(transaction("AUTHORIZATION", "APPROVED", 3), ORDER_ID, "AUTHORIZED"),
        historyLine(
            transaction("AUTHORIZATION", 80399, "DECLINED", "insufficient_funds", 3, null),
            ORDER_ID,
            "DECLINED"));
    var id = started();

    send(id, "I see two charges on my card")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Nothing has been charged yet. On Order #7F1C2A3B, the Authorization of 803.99 EUR on"
                + " 3 November 2026 was declined (insufficient_funds), so nothing was taken, and"
                + " the Authorization of 803.99 EUR on 3 November 2026 is a hold on your card, not"
                + " a charge: the Capture charges it when the Order ships, or your bank releases"
                + " it.");
  }

  @Test
  void aDeclinedCaptureChargedNothing() {
    stubHistory(
        historyLine(
            transaction("CAPTURE", 80399, "DECLINED", "authorization_expired", 4, null),
            ORDER_ID,
            "AUTHORIZED"),
        historyLine(transaction("AUTHORIZATION", "APPROVED", 3), ORDER_ID, "AUTHORIZED"));
    var id = started();

    send(id, "Was I charged?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Nothing has been charged yet. On Order #7F1C2A3B, the Authorization of 803.99 EUR on"
                + " 3 November 2026 is a hold on your card, not a charge: the Capture charges it"
                + " when the Order ships, or your bank releases it, and the Capture of 803.99 EUR"
                + " on 4 November 2026 was declined (authorization_expired), so nothing was"
                + " charged, and the Order is held.");
  }

  @Test
  void aRefundsAmountAndWhatItWasForAreGiven() {
    stubHistory(
        historyLine(
            transaction("REFUND", 12900, "APPROVED", null, 6, "RMA-3F2A9C1B"),
            ORDER_ID,
            "PARTIALLY_REFUNDED"),
        historyLine(transaction("CAPTURE", "APPROVED", 4), ORDER_ID, "PARTIALLY_REFUNDED"));
    var id = started();

    send(id, "Has my refund gone through?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Your Refund of 129.00 EUR for RMA-3F2A9C1B on Order #7F1C2A3B was approved on 6"
                + " November 2026. Your bank may take a few days to show it.")
        .jsonPath("$.references[0].id")
        .isEqualTo(ORDER_ID);
  }

  @Test
  void aDeclinedRefundIsSaidSoWithItsReason() {
    stubHistory(
        historyLine(
            transaction("REFUND", 12900, "DECLINED", "refund_window_closed", 6, "RMA-3F2A9C1B"),
            ORDER_ID,
            "CAPTURED"));
    var id = started();

    send(id, "Where is my refund?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo(
            "Your Refund of 129.00 EUR for RMA-3F2A9C1B on Order #7F1C2A3B was declined on 6"
                + " November 2026 (refund_window_closed).");
  }

  @Test
  void noRefundIsSaidSo() {
    stubHistory(historyLine(transaction("CAPTURE", "APPROVED", 4), ORDER_ID, "CAPTURED"));
    var id = started();

    send(id, "Has my refund gone through?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("I couldn't find a Refund on your Payments.")
        .jsonPath("$.references")
        .isEmpty();
  }

  @Test
  void anOrderWithoutPaymentsIsSaidSo() {
    stubOrderByReference();
    stubPayments(ORDER_ID);
    var id = started();

    send(id, "Was I charged for #7F1C2A3B?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("I couldn't find a Payment for Order #7F1C2A3B.");
  }

  @Test
  void paymentFailingIsSaidSo() {
    stubHistory(serviceUnavailable());
    var id = started();

    send(id, "Why was I charged twice?")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.text")
        .isEqualTo("I couldn't look up your Payments just now. Please try again in a moment.");
  }
}
