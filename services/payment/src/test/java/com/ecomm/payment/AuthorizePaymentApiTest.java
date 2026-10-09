package com.ecomm.payment;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** The checkout Saga authorizes an Order's payment for a Customer, paid with a payment method. */
class AuthorizePaymentApiTest extends PaymentApiTest {

  @Test
  void aPaymentIsRecordedAsAuthorizedWithAGatewayReference() {
    authorize(
            """
            {"customerId": "customer-42", "orderId": "order-1", "paymentMethod": "tok_approve", "amount": {"amountMinor": 79900, "currency": "EUR"}}
            """)
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.id")
        .isNotEmpty()
        .jsonPath("$.orderId")
        .isEqualTo("order-1")
        .jsonPath("$.amount.amountMinor")
        .isEqualTo(79900)
        .jsonPath("$.amount.currency")
        .isEqualTo("EUR")
        .jsonPath("$.status")
        .isEqualTo("AUTHORIZED")
        .jsonPath("$.gatewayReference")
        .isNotEmpty();
  }

  @Test
  void eachKeyGetsItsOwnPaymentAndGatewayReference() {
    var body =
        """
        {"customerId": "customer-42", "orderId": "order-twice", "paymentMethod": "tok_approve", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """;
    var first = authorize(body).expectBody(PaymentView.class).returnResult().getResponseBody();
    var second = authorize(body).expectBody(PaymentView.class).returnResult().getResponseBody();

    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(second.gatewayReference()).isNotEqualTo(first.gatewayReference());
  }

  @Test
  void anAuthorizationIsItsPaymentsFirstTransaction() {
    var payment = authorized("order-ledger");

    assertThat(payment.transactions()).hasSize(1);
    var authorization = payment.transactions().getFirst();
    assertThat(authorization.kind()).isEqualTo("AUTHORIZATION");
    assertThat(authorization.outcome()).isEqualTo("APPROVED");
    assertThat(authorization.amount()).isEqualTo(new AmountView(79900, "EUR"));
    assertThat(authorization.gatewayReference()).isEqualTo(payment.gatewayReference());
    assertThat(authorization.declineReason()).isNull();
    assertThat(authorization.gatewayEventId()).isNull();
    assertThat(Instant.parse(authorization.at())).isCloseTo(Instant.now(), within(1, MINUTES));
    assertThat(authorization.backfilled()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {"customerId": "customer-42", "paymentMethod": "tok_approve", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": " ", "paymentMethod": "tok_approve", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": 7, "paymentMethod": "tok_approve", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
         "paymentMethod": "tok_approve", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "paymentMethod": "tok_approve"}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "paymentMethod": "tok_approve", "amount": {"amountMinor": 0, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "paymentMethod": "tok_approve", "amount": {"amountMinor": -100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "paymentMethod": "tok_approve", "amount": {"amountMinor": 1.5, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "paymentMethod": "tok_approve", "amount": {"amountMinor": "100", "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "paymentMethod": "tok_approve", "amount": {"amountMinor": 100}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "paymentMethod": "tok_approve", "amount": {"amountMinor": 100, "currency": "XYZ"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "paymentMethod": "tok_approve", "amount": {"amountMinor": 100, "currency": "eur"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "paymentMethod": "tok_approve", "amount": {"amountMinor": 99999999999999999999, "currency": "EUR"}}
        """,
        """
        {"orderId": "order-bad", "paymentMethod": "tok_approve", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": " ", "orderId": "order-bad",
         "paymentMethod": "tok_approve", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": 42, "orderId": "order-bad",
         "paymentMethod": "tok_approve", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        "{\"customerId\": \"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\", \"orderId\": \"order-bad\", \"paymentMethod\": \"tok_approve\", \"amount\": {\"amountMinor\": 100, \"currency\": \"EUR\"}}",
        """
        {"customerId": "customer-42", "orderId": "order-bad", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "paymentMethod": " ",
         "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "paymentMethod": 42,
         "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "paymentMethod": {"token": "tok_approve"},
         "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        "{\"customerId\": \"customer-42\", \"orderId\": \"order-bad\", \"paymentMethod\": \"tok_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\", \"amount\": {\"amountMinor\": 100, \"currency\": \"EUR\"}}",
        "not json"
      })
  void anInvalidRequestIsABadRequest(String body) {
    authorize(body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }
}
