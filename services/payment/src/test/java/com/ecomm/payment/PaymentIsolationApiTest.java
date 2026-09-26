package com.ecomm.payment;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Only the Customer who authorized a Payment can read it; Staff have no Payments. */
class PaymentIsolationApiTest extends PaymentApiTest {

  private static final String PAYMENT =
      """
      {"orderId": "order-isolated", "amount": {"amountMinor": 100, "currency": "EUR"}}
      """;

  @Test
  void anotherCustomerCannotSeeAPayment() {
    var id = authorize(PAYMENT).expectBody(PaymentView.class).returnResult().getResponseBody().id();

    http.get()
        .uri("/payments/{id}", id)
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("customer-7", "CUSTOMER")))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void staffCannotAuthorizeAPayment() {
    http.post()
        .uri("/payments")
        .headers(h -> h.setBearerAuth(staffToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(PAYMENT)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void staffCannotReadAPayment() {
    var id = authorize(PAYMENT).expectBody(PaymentView.class).returnResult().getResponseBody().id();

    http.get()
        .uri("/payments/{id}", id)
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  private static String staffToken() {
    return FakeKeycloak.token("staff-1", "STAFF");
  }
}
