package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Each change to a Payment publishes one {@code payment.payment} event: keyed by the Payment's ID,
 * valid against its schema, carrying the request's Correlation ID, its snapshot with its
 * transactions, and a higher version than the last. A request that changes nothing publishes
 * nothing.
 */
class PaymentEventsApiTest extends PaymentApiTest {

  @Test
  void anAuthorizationPublishesThePayment() {
    var payment =
        http.post()
            .uri("/payments")
            .headers(h -> h.setBearerAuth(checkoutToken()))
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .header("X-Correlation-Id", "payment-events-authorize-1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {"customerId": "customer-events", "orderId": "order-events", "paymentMethod": "tok_approve",
                 "amount": {"amountMinor": 79900, "currency": "EUR"}}
                """)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    var records = PaymentEvents.keyed(payment.id(), 1);

    assertThat(records).hasSize(1);
    var record = records.getFirst();
    assertThat(PaymentEvents.schemaViolationsOf(record)).isEmpty();
    assertThat(
            new String(
                record.headers().lastHeader("X-Correlation-Id").value(), StandardCharsets.UTF_8))
        .isEqualTo("payment-events-authorize-1");
    var event = PaymentEvents.valueOf(record);
    assertThat(event.get("paymentId").asText()).isEqualTo(payment.id());
    assertThat(event.get("change").asText()).isEqualTo("AUTHORIZED");
    assertThat(event.get("version").asLong()).isEqualTo(1);
    var snapshot = event.get("payment");
    assertThat(snapshot.get("orderId").asText()).isEqualTo("order-events");
    assertThat(snapshot.get("customerId").asText()).isEqualTo("customer-events");
    assertThat(snapshot.at("/amount/amountMinor").asLong()).isEqualTo(79900);
    assertThat(snapshot.at("/amount/currency").asText()).isEqualTo("EUR");
    assertThat(snapshot.get("status").asText()).isEqualTo("AUTHORIZED");
    assertThat(snapshot.has("declineReason")).isFalse();
    assertThat(snapshot.get("transactions")).hasSize(1);
    assertThat(snapshot.at("/transactions/0/kind").asText()).isEqualTo("AUTHORIZATION");
    assertThat(snapshot.at("/transactions/0/outcome").asText()).isEqualTo("APPROVED");
    assertThat(snapshot.at("/transactions/0/gatewayReference").asText())
        .isEqualTo(payment.gatewayReference());
    assertThat(snapshot.at("/transactions/0/at").asText())
        .isEqualTo(payment.transactions().getFirst().at());
    assertThat(snapshot.at("/transactions/0/backfilled").asBoolean()).isFalse();
  }

  @Test
  void aDeclinePublishesThePaymentWithItsReason() {
    var payment =
        authorize("order-events-declined", "tok_insufficient_funds")
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    var record = PaymentEvents.keyed(payment.id(), 1).getFirst();

    assertThat(PaymentEvents.schemaViolationsOf(record)).isEmpty();
    var event = PaymentEvents.valueOf(record);
    assertThat(event.get("change").asText()).isEqualTo("DECLINED");
    assertThat(event.at("/payment/status").asText()).isEqualTo("DECLINED");
    assertThat(event.at("/payment/declineReason").asText()).isEqualTo("insufficient_funds");
    assertThat(event.at("/payment/transactions/0/declineReason").asText())
        .isEqualTo("insufficient_funds");
  }

  @Test
  void aVoidPublishesThePaymentWithAHigherVersion() {
    var payment = authorized("order-events-void");
    voidPayment(payment.id(), "customer-42").expectStatus().isOk();

    var records = PaymentEvents.keyed(payment.id(), 2);

    assertThat(records).hasSize(2);
    assertThat(records).allSatisfy(r -> assertThat(PaymentEvents.schemaViolationsOf(r)).isEmpty());
    var events = records.stream().map(PaymentEvents::valueOf).toList();
    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("AUTHORIZED", "VOIDED");
    assertThat(events).extracting(e -> e.get("version").asLong()).containsExactly(1L, 2L);
    var voided = events.getLast().get("payment");
    assertThat(voided.get("status").asText()).isEqualTo("VOIDED");
    assertThat(voided.get("transactions"))
        .extracting(t -> t.get("kind").asText())
        .containsExactly("AUTHORIZATION", "VOID");
  }

  @Test
  void aRequestThatChangesNothingPublishesNothing() {
    var body =
        """
        {"customerId": "customer-42", "orderId": "order-events-repeat", "paymentMethod": "tok_approve",
         "amount": {"amountMinor": 79900, "currency": "EUR"}}
        """;
    var payment =
        authorizeWithKey("events-repeat-1", body)
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();
    authorizeWithKey("events-repeat-1", body).expectStatus().isCreated();
    voidPayment(payment.id(), "customer-42").expectStatus().isOk();
    voidPayment(payment.id(), "customer-42").expectStatus().isOk();

    var events =
        PaymentEvents.keyed(payment.id(), 2, Duration.ofSeconds(20)).stream()
            .map(PaymentEvents::valueOf)
            .toList();

    assertThat(events)
        .extracting(e -> e.get("change").asText())
        .containsExactly("AUTHORIZED", "VOIDED");
  }

  @Test
  void aDeclinedPaymentsRefusedVoidPublishesNothing() {
    var payment =
        authorize("order-events-not-voidable", "tok_decline")
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();
    voidPayment(payment.id(), "customer-42").expectStatus().isEqualTo(409);

    assertThat(PaymentEvents.keyed(payment.id(), 1))
        .extracting(r -> PaymentEvents.valueOf(r).get("change").asText())
        .containsExactly("DECLINED");
  }
}
