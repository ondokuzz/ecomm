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
 * The fulfillment Saga captures an authorized Payment for the full amount authorized: a {@code
 * CAPTURE} transaction is recorded and the Payment is {@code CAPTURED}. A capture the gateway
 * declines is recorded with its reason and leaves the Payment authorized. Capturing again changes
 * nothing; a pending, declined or voided Payment can't be captured, and a captured one can't be
 * voided.
 */
class CapturePaymentApiTest extends PaymentApiTest {

  @Test
  void anAuthorizedPaymentIsCaptured() {
    var payment = authorized("order-capture");

    var captured =
        capture(payment.id(), "customer-42")
            .expectStatus()
            .isOk()
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    assertThat(captured.id()).isEqualTo(payment.id());
    assertThat(captured.status()).isEqualTo("CAPTURED");
    assertThat(captured.declineReason()).isNull();
    assertThat(captured.gatewayReference()).isEqualTo(payment.gatewayReference());
    assertThat(captured.transactions())
        .extracting(TransactionView::kind, TransactionView::outcome)
        .containsExactly(tuple("AUTHORIZATION", "APPROVED"), tuple("CAPTURE", "APPROVED"));
    var captureTransaction = captured.transactions().getLast();
    assertThat(captureTransaction.amount()).isEqualTo(payment.amount());
    assertThat(captureTransaction.gatewayReference()).isNotBlank();
    assertThat(captureTransaction.declineReason()).isNull();
    assertThat(staffPayments("order-capture")).containsExactly(captured);
  }

  @Test
  void aDeclinedCaptureIsRecordedWithItsReasonAndLeavesThePaymentAuthorized() {
    var payment = authorizedWith("order-capture-declined", "tok_capture_decline");

    capture(payment.id(), "customer-42")
        .expectStatus()
        .isEqualTo(402)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("captureDeclined")
        .jsonPath("$.declineReason")
        .isEqualTo("authorization_expired");

    var recorded = staffPayments("order-capture-declined").getFirst();
    assertThat(recorded.status()).isEqualTo("AUTHORIZED");
    assertThat(recorded.declineReason()).isNull();
    assertThat(recorded.transactions())
        .extracting(TransactionView::kind, TransactionView::outcome, TransactionView::declineReason)
        .containsExactly(
            tuple("AUTHORIZATION", "APPROVED", null),
            tuple("CAPTURE", "DECLINED", "authorization_expired"));
  }

  @Test
  void aPaymentWhoseCaptureWasDeclinedCanStillBeVoided() {
    var payment = authorizedWith("order-capture-declined-void", "tok_capture_decline");
    capture(payment.id(), "customer-42").expectStatus().isEqualTo(402);

    voidPayment(payment.id(), "customer-42")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("VOIDED");
  }

  @Test
  void capturingACapturedPaymentAgainAnswersTheSameAndRecordsNothing() {
    var payment = authorized("order-capture-twice");
    var captured =
        capture(payment.id(), "customer-42")
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    capture(payment.id(), "customer-42")
        .expectStatus()
        .isOk()
        .expectBody(PaymentView.class)
        .isEqualTo(captured);

    assertThat(staffPayments("order-capture-twice").getFirst().transactions()).hasSize(2);
  }

  @Test
  void repeatingTheRequestReplaysTheFirstResponse() {
    var payment = authorized("order-capture-replayed");
    var captured =
        captureWithKey(payment.id(), "customer-42", "capture-replayed-1")
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    captureWithKey(payment.id(), "customer-42", "capture-replayed-1")
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Idempotent-Replayed", "true")
        .expectBody(PaymentView.class)
        .isEqualTo(captured);
  }

  @Test
  void aKeyIsRequired() {
    var payment = authorized("order-capture-no-key");

    http.post()
        .uri("/payments/{id}/capture", payment.id())
        .headers(h -> h.setBearerAuth(orchestrationToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"customerId\": \"customer-42\"}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("idempotencyKeyRequired");

    assertThat(staffPayments("order-capture-no-key").getFirst().status()).isEqualTo("AUTHORIZED");
  }

  @Test
  void theKeyIsPassedOnToTheGateway() {
    // A declined capture isn't replayed, so asking again with the same key reaches the gateway
    // again, which answers with its first reference; another key is a new capture.
    var payment = authorizedWith("order-capture-gateway-key", "tok_capture_decline");
    captureWithKey(payment.id(), "customer-42", "capture-key-1").expectStatus().isEqualTo(402);
    captureWithKey(payment.id(), "customer-42", "capture-key-1").expectStatus().isEqualTo(402);
    captureWithKey(payment.id(), "customer-42", "capture-key-2").expectStatus().isEqualTo(402);

    var references =
        staffPayments("order-capture-gateway-key").getFirst().transactions().stream()
            .skip(1)
            .map(TransactionView::gatewayReference)
            .toList();
    assertThat(references).hasSize(3);
    assertThat(references.get(1)).isEqualTo(references.get(0));
    assertThat(references.get(2)).isNotEqualTo(references.get(0));
  }

  @ParameterizedTest
  @ValueSource(strings = {"tok_async_approve", "tok_decline"})
  void aPendingOrDeclinedPaymentCanNotBeCaptured(String paymentMethod) {
    var orderId = "order-capture-" + paymentMethod;
    var payment =
        authorize(orderId, paymentMethod)
            .expectStatus()
            .isCreated()
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    capture(payment.id(), "customer-42")
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("paymentNotCapturable");

    assertThat(staffPayments(orderId).getFirst().transactions()).hasSize(1);
  }

  @Test
  void aVoidedPaymentCanNotBeCaptured() {
    var payment = authorized("order-capture-voided");
    voidPayment(payment.id(), "customer-42").expectStatus().isOk();

    capture(payment.id(), "customer-42")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("paymentNotCapturable");

    assertThat(staffPayments("order-capture-voided").getFirst().status()).isEqualTo("VOIDED");
  }

  @Test
  void aCapturedPaymentCanNotBeVoided() {
    var payment = authorized("order-capture-then-void");
    capture(payment.id(), "customer-42").expectStatus().isOk();

    voidPayment(payment.id(), "customer-42")
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("alreadyCaptured");

    var recorded = staffPayments("order-capture-then-void").getFirst();
    assertThat(recorded.status()).isEqualTo("CAPTURED");
    assertThat(recorded.transactions()).hasSize(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"6f1c2d3e-0000-4000-8000-000000000000", "not-a-payment-id"})
  void anUnknownPaymentIsNotFound(String id) {
    capture(id, "customer-42").expectStatus().isNotFound();
  }

  @Test
  void anotherCustomersPaymentIsNotFound() {
    var payment = authorized("order-capture-other");

    capture(payment.id(), "customer-7").expectStatus().isNotFound();

    assertThat(staffPayments("order-capture-other").getFirst().status()).isEqualTo("AUTHORIZED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"customerId\": \" \"}", "{\"customerId\": 42}", "not json"})
  void aCaptureThatDoesntNameTheCustomerIsABadRequest(String body) {
    var payment = authorized("order-capture-bad");

    http.post()
        .uri("/payments/{id}/capture", payment.id())
        .headers(h -> h.setBearerAuth(orchestrationToken()))
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "STAFF", "CHECKOUT"})
  void onlyOrchestrationCanCaptureAPayment(String role) {
    var payment = authorized("order-capture-forbidden");

    http.post()
        .uri("/payments/{id}/capture", payment.id())
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("customer-42", role)))
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"customerId\": \"customer-42\"}")
        .exchange()
        .expectStatus()
        .isForbidden();

    assertThat(staffPayments("order-capture-forbidden").getFirst().status())
        .isEqualTo("AUTHORIZED");
  }

  private PaymentView authorizedWith(String orderId, String paymentMethod) {
    return authorize(orderId, paymentMethod)
        .expectStatus()
        .isCreated()
        .expectBody(PaymentView.class)
        .returnResult()
        .getResponseBody();
  }
}
