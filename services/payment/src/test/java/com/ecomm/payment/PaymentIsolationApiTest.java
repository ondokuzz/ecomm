package com.ecomm.payment;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * Only Checkout authorizes Payments; only the Customer a Payment was authorized for can read it.
 */
class PaymentIsolationApiTest extends PaymentApiTest {

  private static final String PAYMENT =
      """
      {"customerId": "customer-42", "orderId": "order-isolated", "paymentMethod": "tok_approve", "amount": {"amountMinor": 100, "currency": "EUR"}}
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
  void thePaymentBelongsToTheCustomerCheckoutNamed() {
    var id =
        authorize(
                """
                {"customerId": "customer-9", "orderId": "order-named",
                 "paymentMethod": "tok_approve", "amount": {"amountMinor": 100, "currency": "EUR"}}
                """)
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody()
            .id();

    http.get()
        .uri("/payments/{id}", id)
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("customer-9", "CUSTOMER")))
        .exchange()
        .expectStatus()
        .isOk();
    http.get()
        .uri("/payments/{id}", id)
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "STAFF"})
  void onlyCheckoutCanAuthorizeAPayment(String role) {
    http.post()
        .uri("/payments")
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("someone-" + role, role)))
        .header("Idempotency-Key", "isolation-" + role)
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
}
