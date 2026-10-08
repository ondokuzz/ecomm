package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Authorizing takes an {@code Idempotency-Key}: a repeat authorizes once, and the key is passed on
 * to the gateway, which answers a repeated key with its first answer and reference.
 */
class IdempotentAuthorizationApiTest extends PaymentApiTest {

  private static final String BODY =
      """
      {"customerId": "customer-42", "orderId": "%s", "paymentMethod": "tok_approve",
       "amount": {"amountMinor": 79900, "currency": "EUR"}}
      """;

  @Test
  void aKeyIsRequired() {
    http.post()
        .uri("/payments")
        .headers(h -> h.setBearerAuth(checkoutToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(BODY.formatted("order-no-key"))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("idempotencyKeyRequired");

    assertThat(staffPayments("order-no-key")).isEmpty();
  }

  @Test
  void repeatingTheRequestAuthorizesOnce() {
    var body = BODY.formatted("order-repeated");
    var first =
        authorizeWithKey("repeat-1", body)
            .expectStatus()
            .isCreated()
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    var repeat =
        authorizeWithKey("repeat-1", body)
            .expectStatus()
            .isCreated()
            .expectHeader()
            .valueEquals("Idempotent-Replayed", "true")
            .expectHeader()
            .valueEquals("Location", "/payments/" + first.id())
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    assertThat(repeat).isEqualTo(first);
    assertThat(staffPayments("order-repeated"))
        .extracting(PaymentView::id)
        .containsExactly(first.id());
  }

  @Test
  void theKeyIsPassedOnToTheGateway() {
    // Keys are scoped to their caller, so another Checkout client with the same key reaches the
    // gateway again, with the same key: the gateway answers with its first reference.
    var body = BODY.formatted("order-gateway-key");
    var first =
        authorizeWithKey("gateway-key-1", body).expectBody(PaymentView.class).returnResult();

    var second =
        http.post()
            .uri("/payments")
            .headers(h -> h.setBearerAuth(FakeKeycloak.token("checkout-2", "CHECKOUT")))
            .header("Idempotency-Key", "gateway-key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectHeader()
            .doesNotExist("Idempotent-Replayed")
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    assertThat(second.id()).isNotEqualTo(first.getResponseBody().id());
    assertThat(second.gatewayReference()).isEqualTo(first.getResponseBody().gatewayReference());
  }

  @Test
  void aGatewayFailureRecordsNothingSoTheKeyCanBeRetried() {
    var failing =
        """
        {"customerId": "customer-42", "orderId": "order-retried", "paymentMethod": "tok_gateway_error",
         "amount": {"amountMinor": 79900, "currency": "EUR"}}
        """;
    authorizeWithKey("retried-1", failing).expectStatus().isEqualTo(502);

    authorizeWithKey("retried-1", failing)
        .expectStatus()
        .isEqualTo(502)
        .expectHeader()
        .doesNotExist("Idempotent-Replayed");
    assertThat(staffPayments("order-retried")).isEmpty();
  }
}
