package com.ecomm.payment;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Every Payment endpoint needs a token. */
class PaymentSecurityApiTest extends PaymentApiTest {

  @Test
  void authorizingNeedsAToken() {
    http.post()
        .uri("/payments")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"orderId": "order-anon", "amount": {"amountMinor": 100, "currency": "EUR"}}
            """)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void readingNeedsAToken() {
    var id =
        authorize(
                """
                {"orderId": "order-anon-read", "amount": {"amountMinor": 100, "currency": "EUR"}}
                """)
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody()
            .id();

    http.get()
        .uri("/payments/{id}", id)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }
}
