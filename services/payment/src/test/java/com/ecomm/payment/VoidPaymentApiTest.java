package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * Checkout voids an authorized Payment, releasing it through the gateway: a {@code VOID}
 * transaction is recorded and the Payment is {@code VOIDED}. Voiding again changes nothing; a
 * declined Payment can't be voided.
 */
class VoidPaymentApiTest extends PaymentApiTest {

  @Test
  void anAuthorizedPaymentIsVoided() {
    var payment = authorized("order-void");

    var voided =
        voidPayment(payment.id(), "customer-42")
            .expectStatus()
            .isOk()
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    assertThat(voided.id()).isEqualTo(payment.id());
    assertThat(voided.status()).isEqualTo("VOIDED");
    assertThat(voided.gatewayReference()).isEqualTo(payment.gatewayReference());
    assertThat(voided.transactions())
        .extracting(TransactionView::kind, TransactionView::outcome)
        .containsExactly(tuple("AUTHORIZATION", "APPROVED"), tuple("VOID", "APPROVED"));
    var voidTransaction = voided.transactions().getLast();
    assertThat(voidTransaction.amount()).isEqualTo(payment.amount());
    assertThat(voidTransaction.gatewayReference()).isNotBlank();
    assertThat(voidTransaction.backfilled()).isFalse();
    assertThat(staffPayments("order-void")).containsExactly(voided);
  }

  @Test
  void voidingAVoidedPaymentAgainAnswersTheSameAndRecordsNothing() {
    var payment = authorized("order-void-twice");
    var voided =
        voidPayment(payment.id(), "customer-42")
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    voidPayment(payment.id(), "customer-42")
        .expectStatus()
        .isOk()
        .expectBody(PaymentView.class)
        .isEqualTo(voided);

    assertThat(staffPayments("order-void-twice").getFirst().transactions()).hasSize(2);
  }

  @Test
  void aDeclinedPaymentCanNotBeVoided() {
    var payment =
        authorize("order-void-declined", "tok_decline")
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    voidPayment(payment.id(), "customer-42")
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("paymentNotVoidable");

    assertThat(staffPayments("order-void-declined").getFirst().transactions()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"6f1c2d3e-0000-4000-8000-000000000000", "not-a-payment-id"})
  void anUnknownPaymentIsNotFound(String id) {
    voidPayment(id, "customer-42").expectStatus().isNotFound();
  }

  @Test
  void anotherCustomersPaymentIsNotFound() {
    var payment = authorized("order-void-other");

    voidPayment(payment.id(), "customer-7").expectStatus().isNotFound();

    assertThat(staffPayments("order-void-other").getFirst().status()).isEqualTo("AUTHORIZED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"customerId\": \" \"}", "{\"customerId\": 42}", "not json"})
  void aVoidThatDoesntNameTheCustomerIsABadRequest(String body) {
    var payment = authorized("order-void-bad");

    http.post()
        .uri("/payments/{id}/void", payment.id())
        .headers(h -> h.setBearerAuth(checkoutToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "STAFF"})
  void onlyCheckoutCanVoidAPayment(String role) {
    var payment = authorized("order-void-forbidden");

    http.post()
        .uri("/payments/{id}/void", payment.id())
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("customer-42", role)))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"customerId\": \"customer-42\"}")
        .exchange()
        .expectStatus()
        .isForbidden();

    assertThat(staffPayments("order-void-forbidden").getFirst().status()).isEqualTo("AUTHORIZED");
  }
}
