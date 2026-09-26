package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Checkout authorizes an Order's payment; the mock gateway always approves it. */
class AuthorizePaymentApiTest extends PaymentApiTest {

  @Test
  void aPaymentIsRecordedAsAuthorizedWithAGatewayReference() {
    authorize(
            """
            {"orderId": "order-1", "amount": {"amountMinor": 79900, "currency": "EUR"}}
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
        {"orderId": "order-twice", "amount": {"amountMinor": 100, "currency": "EUR"}}
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
        {"amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"orderId": " ", "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"orderId": 7, "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"orderId": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
         "amount": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"orderId": "order-bad"}
        """,
        """
        {"orderId": "order-bad", "amount": {"amountMinor": 0, "currency": "EUR"}}
        """,
        """
        {"orderId": "order-bad", "amount": {"amountMinor": -100, "currency": "EUR"}}
        """,
        """
        {"orderId": "order-bad", "amount": {"amountMinor": 1.5, "currency": "EUR"}}
        """,
        """
        {"orderId": "order-bad", "amount": {"amountMinor": "100", "currency": "EUR"}}
        """,
        """
        {"orderId": "order-bad", "amount": {"amountMinor": 100}}
        """,
        """
        {"orderId": "order-bad", "amount": {"amountMinor": 100, "currency": "XYZ"}}
        """,
        """
        {"orderId": "order-bad", "amount": {"amountMinor": 100, "currency": "eur"}}
        """,
        """
        {"orderId": "order-bad", "amount": {"amountMinor": 99999999999999999999, "currency": "EUR"}}
        """,
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
