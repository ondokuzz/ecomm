package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * The returns Saga gives back money a Capture took, as {@code REFUND} transactions naming what each
 * is for: in parts, the Payment {@code PARTIALLY_REFUNDED}, until the Refunds reach the amount
 * captured and it is {@code REFUNDED}. More than is left, or another Currency, is refused before
 * the gateway is asked; a Refund the gateway declines is recorded with its reason and changes
 * nothing. Only money that was captured can be given back, and only Orchestration can give it.
 */
class RefundPaymentApiTest extends PaymentApiTest {

  @Test
  void partOfACapturedPaymentIsRefunded() {
    var payment = captured("order-refund-part");

    var refunded =
        refund(payment.id(), 30000, "RMA-3F2A9C1B")
            .expectStatus()
            .isOk()
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    assertThat(refunded.status()).isEqualTo("PARTIALLY_REFUNDED");
    assertThat(refunded.transactions())
        .extracting(TransactionView::kind, TransactionView::outcome, TransactionView::reference)
        .containsExactly(
            tuple("AUTHORIZATION", "APPROVED", null),
            tuple("CAPTURE", "APPROVED", null),
            tuple("REFUND", "APPROVED", "RMA-3F2A9C1B"));
    var refund = refunded.transactions().getLast();
    assertThat(refund.amount()).isEqualTo(new AmountView(30000, "EUR"));
    assertThat(refund.gatewayReference()).isNotBlank();
    assertThat(staffPayments("order-refund-part")).containsExactly(refunded);
  }

  @Test
  void refundsUpToTheAmountCapturedRefundThePayment() {
    var payment = captured("order-refund-whole");
    refund(payment.id(), 30000, "RMA-00000001").expectStatus().isOk();

    refund(payment.id(), 49900, "RMA-00000002")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("REFUNDED");

    refund(payment.id(), 1, "RMA-00000003")
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("paymentNotRefundable");
    assertThat(staffPayments("order-refund-whole").getFirst().transactions()).hasSize(4);
  }

  @Test
  void moreThanIsLeftIsRefusedWithoutAskingTheGateway() {
    var payment = captured("order-refund-too-much");
    refund(payment.id(), 30000, "RMA-00000001").expectStatus().isOk();

    refund(payment.id(), 49901, "RMA-00000002")
        .expectStatus()
        .isEqualTo(422)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("refundExceedsCaptured");

    var recorded = staffPayments("order-refund-too-much").getFirst();
    assertThat(recorded.status()).isEqualTo("PARTIALLY_REFUNDED");
    assertThat(recorded.transactions()).hasSize(3);
  }

  @Test
  void aRefundInAnotherCurrencyIsRefused() {
    var payment = captured("order-refund-currency");

    refundWithKey(payment.id(), UUID.randomUUID().toString(), refundBody(100, "USD", "RMA-1"))
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("refundExceedsCaptured");

    assertThat(staffPayments("order-refund-currency").getFirst().status()).isEqualTo("CAPTURED");
  }

  @Test
  void aDeclinedRefundIsRecordedWithItsReasonAndChangesNothing() {
    var payment = captured("order-refund-declined", "tok_refund_decline");

    refund(payment.id(), 79900, "RMA-00000001")
        .expectStatus()
        .isEqualTo(402)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("refundDeclined")
        .jsonPath("$.declineReason")
        .isEqualTo("refund_window_closed");

    var recorded = staffPayments("order-refund-declined").getFirst();
    assertThat(recorded.status()).isEqualTo("CAPTURED");
    assertThat(recorded.declineReason()).isNull();
    assertThat(recorded.transactions())
        .extracting(
            TransactionView::kind,
            TransactionView::outcome,
            TransactionView::declineReason,
            TransactionView::reference)
        .containsExactly(
            tuple("AUTHORIZATION", "APPROVED", null, null),
            tuple("CAPTURE", "APPROVED", null, null),
            tuple("REFUND", "DECLINED", "refund_window_closed", "RMA-00000001"));
  }

  @Test
  void repeatingTheRequestReplaysTheFirstResponseAndRefundsOnce() {
    var payment = captured("order-refund-replayed");
    var body = refundBody(30000, "EUR", "RMA-00000001");
    var refunded =
        refundWithKey(payment.id(), "refund-replayed-1", body)
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    refundWithKey(payment.id(), "refund-replayed-1", body)
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Idempotent-Replayed", "true")
        .expectBody(PaymentView.class)
        .isEqualTo(refunded);

    assertThat(staffPayments("order-refund-replayed").getFirst().transactions()).hasSize(3);
  }

  @Test
  void theKeyIsPassedOnToTheGateway() {
    // A declined Refund isn't replayed, so asking again with the same key reaches the gateway
    // again, which answers with its first reference; another key is a new Refund.
    var payment = captured("order-refund-gateway-key", "tok_refund_decline");
    var body = refundBody(100, "EUR", "RMA-00000001");
    refundWithKey(payment.id(), "refund-key-1", body).expectStatus().isEqualTo(402);
    refundWithKey(payment.id(), "refund-key-1", body).expectStatus().isEqualTo(402);
    refundWithKey(payment.id(), "refund-key-2", body).expectStatus().isEqualTo(402);

    var references =
        staffPayments("order-refund-gateway-key").getFirst().transactions().stream()
            .skip(2)
            .map(TransactionView::gatewayReference)
            .toList();
    assertThat(references).hasSize(3);
    assertThat(references.get(1)).isEqualTo(references.get(0));
    assertThat(references.get(2)).isNotEqualTo(references.get(0));
  }

  @Test
  void aKeyIsRequired() {
    var payment = captured("order-refund-no-key");

    http.post()
        .uri("/payments/{id}/refunds", payment.id())
        .headers(h -> h.setBearerAuth(orchestrationToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(refundBody(100, "EUR", "RMA-1"))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("idempotencyKeyRequired");

    assertThat(staffPayments("order-refund-no-key").getFirst().status()).isEqualTo("CAPTURED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"tok_approve", "tok_async_approve", "tok_decline"})
  void aPaymentThatWasNeverCapturedCanNotBeRefunded(String paymentMethod) {
    var orderId = "order-refund-uncaptured-" + paymentMethod;
    var payment =
        authorize(orderId, paymentMethod)
            .expectStatus()
            .isCreated()
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    refund(payment.id(), 100, "RMA-1")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("paymentNotRefundable");

    assertThat(staffPayments(orderId).getFirst().transactions()).hasSize(1);
  }

  @Test
  void aVoidedPaymentCanNotBeRefunded() {
    var payment = authorized("order-refund-voided");
    voidPayment(payment.id(), "customer-42").expectStatus().isOk();

    refund(payment.id(), 100, "RMA-1")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("paymentNotRefundable");
  }

  @Test
  void aRefundedPaymentCanNotBeVoided() {
    var payment = captured("order-refund-then-void");
    refund(payment.id(), 100, "RMA-1").expectStatus().isOk();

    voidPayment(payment.id(), "customer-42")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("alreadyCaptured");
  }

  @Test
  void capturingARefundedPaymentAgainAnswersItAsCapturedAndRecordsNothing() {
    var payment = captured("order-refund-then-capture");
    refund(payment.id(), 100, "RMA-1").expectStatus().isOk();

    capture(payment.id(), "customer-42")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("PARTIALLY_REFUNDED");

    assertThat(staffPayments("order-refund-then-capture").getFirst().transactions()).hasSize(3);
  }

  @Test
  void anotherCustomersPaymentIsNotFound() {
    var payment = captured("order-refund-other");

    refundWithKey(
            payment.id(),
            UUID.randomUUID().toString(),
            refundBody(100, "EUR", "RMA-1").replace("customer-42", "customer-7"))
        .expectStatus()
        .isNotFound();

    assertThat(staffPayments("order-refund-other").getFirst().status()).isEqualTo("CAPTURED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"6f1c2d3e-0000-4000-8000-000000000000", "not-a-payment-id"})
  void anUnknownPaymentIsNotFound(String id) {
    refund(id, 100, "RMA-1").expectStatus().isNotFound();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"amount\": {\"amountMinor\": 100, \"currency\": \"EUR\"}, \"reference\": \"RMA-1\"}",
        "{\"customerId\": \"customer-42\", \"reference\": \"RMA-1\"}",
        "{\"customerId\": \"customer-42\", \"amount\": {\"amountMinor\": 0, \"currency\": \"EUR\"},"
            + " \"reference\": \"RMA-1\"}",
        "{\"customerId\": \"customer-42\", \"amount\": {\"amountMinor\": 1.5, \"currency\": \"EUR\"},"
            + " \"reference\": \"RMA-1\"}",
        "{\"customerId\": \"customer-42\", \"amount\": {\"amountMinor\": 100, \"currency\": \"EURO\"},"
            + " \"reference\": \"RMA-1\"}",
        "{\"customerId\": \"customer-42\", \"amount\": {\"amountMinor\": 100, \"currency\": \"EUR\"}}",
        "{\"customerId\": \"customer-42\", \"amount\": {\"amountMinor\": 100, \"currency\": \"EUR\"},"
            + " \"reference\": \" \"}",
        "not json"
      })
  void aRefundThatDoesntSayWhoseHowMuchOrWhatForIsABadRequest(String body) {
    var payment = captured("order-refund-bad");

    refundWithKey(payment.id(), UUID.randomUUID().toString(), body).expectStatus().isBadRequest();
  }

  @Test
  void aReferenceLongerThan64CharactersIsABadRequest() {
    var payment = captured("order-refund-long-reference");

    refundWithKey(
            payment.id(), UUID.randomUUID().toString(), refundBody(100, "EUR", "R".repeat(65)))
        .expectStatus()
        .isBadRequest();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "STAFF", "CHECKOUT"})
  void onlyOrchestrationCanRefundAPayment(String role) {
    var payment = captured("order-refund-forbidden");

    http.post()
        .uri("/payments/{id}/refunds", payment.id())
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("customer-42", role)))
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(refundBody(100, "EUR", "RMA-1"))
        .exchange()
        .expectStatus()
        .isForbidden();

    assertThat(staffPayments("order-refund-forbidden").getFirst().status()).isEqualTo("CAPTURED");
  }
}
