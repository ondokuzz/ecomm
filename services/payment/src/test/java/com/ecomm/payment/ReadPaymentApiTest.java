package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** A recorded Payment can be read back by its ID. */
class ReadPaymentApiTest extends PaymentApiTest {

  @Test
  void anAuthorizedPaymentCanBeReadBackAtItsLocation() {
    var created =
        authorize(
                """
                {"orderId": "order-read", "amount": {"amountMinor": 4250, "currency": "USD"}}
                """)
            .expectStatus()
            .isCreated()
            .expectBody(PaymentView.class)
            .returnResult();
    var location = created.getResponseHeaders().getLocation();
    var payment = created.getResponseBody();

    assertThat(location).hasToString("/payments/" + payment.id());
    http.get()
        .uri(location)
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(PaymentView.class)
        .isEqualTo(payment);
    assertThat(payment.orderId()).isEqualTo("order-read");
    assertThat(payment.amount()).isEqualTo(new AmountView(4250, "USD"));
    assertThat(payment.status()).isEqualTo("AUTHORIZED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"6f1c2d3e-0000-4000-8000-000000000000", "not-a-payment-id"})
  void anUnknownPaymentIsNotFound(String id) {
    http.get()
        .uri("/payments/{id}", id)
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }
}
