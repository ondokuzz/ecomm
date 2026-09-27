package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Checkout authorizes an Order's payment for a Customer; the mock gateway always approves it. */
class AuthorizePaymentApiTest extends PaymentApiTest {

  @Test
  void aPaymentIsRecordedAsAuthorizedWithAGatewayReference() {
    authorize(
            """
            {"customerId": "customer-42", "orderId": "order-1", "amount": {"amountMinor": 79900, "currency": "EUR"}}
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
  void eachAuthorizationGetsItsOwnPaymentAndGatewayReference() {
    var body =
        """
        {"customerId": "customer-42", "orderId": "order-twice", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """;
    var first = authorize(body).expectBody(PaymentView.class).returnResult().getResponseBody();
    var second = authorize(body).expectBody(PaymentView.class).returnResult().getResponseBody();

    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(second.gatewayReference()).isNotEqualTo(first.gatewayReference());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {"customerId": "customer-42", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": " ", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": 7, "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
         "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad"}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "amount": {"amountMinor": 0, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "amount": {"amountMinor": -100, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "amount": {"amountMinor": 1.5, "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "amount": {"amountMinor": "100", "currency": "EUR"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "amount": {"amountMinor": 100}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "amount": {"amountMinor": 100, "currency": "XYZ"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "amount": {"amountMinor": 100, "currency": "eur"}}
        """,
        """
        {"customerId": "customer-42", "orderId": "order-bad", "amount": {"amountMinor": 99999999999999999999, "currency": "EUR"}}
        """,
        """
        {"orderId": "order-bad", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": " ", "orderId": "order-bad",
         "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"customerId": 42, "orderId": "order-bad",
         "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        "{\"customerId\": \"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\", \"orderId\": \"order-bad\", \"amount\": {\"amountMinor\": 100, \"currency\": \"EUR\"}}",
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
