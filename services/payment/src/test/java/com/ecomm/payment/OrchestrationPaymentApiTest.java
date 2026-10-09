package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The checkout Saga authorizes, awaits and voids a Customer's Payments with Orchestration's own
 * token, naming the Customer: in the body of a command, and as {@code customerId} when it reads
 * one.
 */
class OrchestrationPaymentApiTest extends PaymentApiTest {

  private static final String ORCHESTRATION = FakeKeycloak.token("orchestration", "ORCHESTRATION");

  @Test
  void orchestrationAuthorizesAPaymentAndARepeatReplaysIt() {
    var key = "session-1:" + UUID.randomUUID() + ":authorize";
    var body = paymentOf("order-orch-" + UUID.randomUUID(), "tok_approve");

    var first = orchestrationAuthorizes(key, body).returnResult(PaymentView.class);
    var repeat = orchestrationAuthorizes(key, body).returnResult(PaymentView.class);

    assertThat(first.getStatus().value()).isEqualTo(201);
    assertThat(first.getResponseBody().status()).isEqualTo("AUTHORIZED");
    assertThat(repeat.getResponseHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
    assertThat(repeat.getResponseBody()).isEqualTo(first.getResponseBody());
    assertThat(staffPayments(first.getResponseBody().orderId())).hasSize(1);
  }

  @Test
  void orchestrationNeedsAKeyToAuthorize() {
    orchestrationAuthorizes(null, paymentOf("order-orch-nokey", "tok_approve"))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("idempotencyKeyRequired");
  }

  @Test
  void orchestrationReadsTheNamedCustomersPayment() {
    var payment = authorized("order-orch-read-" + UUID.randomUUID());

    var read = orchestrationReads(payment.id(), "customer-42");

    read.expectStatus().isOk().expectBody(PaymentView.class).isEqualTo(payment);
  }

  @Test
  void orchestrationReadingAPaymentForAnotherCustomerFindsNone() {
    var payment = authorized("order-orch-other-" + UUID.randomUUID());

    orchestrationReads(payment.id(), "customer-43").expectStatus().isNotFound();
  }

  @Test
  void orchestrationVoidsAPaymentAndVoidingAgainChangesNothing() {
    var payment = authorized("order-orch-void-" + UUID.randomUUID());

    var voided = orchestrationVoids(payment.id(), "customer-42");
    var again = orchestrationVoids(payment.id(), "customer-42");

    voided
        .expectStatus()
        .isOk()
        .expectBody(PaymentView.class)
        .value(p -> assertThat(p.status()).isEqualTo("VOIDED"));
    again
        .expectStatus()
        .isOk()
        .expectBody(PaymentView.class)
        .value(p -> assertThat(p.transactions()).hasSize(2));
  }

  @Test
  void aCustomerCanNeitherAuthorizeNorVoidNorReadAsOrchestration() {
    var payment = authorized("order-orch-cust-" + UUID.randomUUID());

    http.post()
        .uri("/payments")
        .headers(h -> h.setBearerAuth(customerToken()))
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(paymentOf("order-orch-cust", "tok_approve"))
        .exchange()
        .expectStatus()
        .isForbidden();
    http.post()
        .uri("/payments/{id}/void", payment.id())
        .headers(h -> h.setBearerAuth(customerToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"customerId\": \"customer-42\"}")
        .exchange()
        .expectStatus()
        .isForbidden();
    http.get()
        .uri("/payments/{id}?customerId=customer-42", payment.id())
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  private static String paymentOf(String orderId, String paymentMethod) {
    return """
        {"customerId": "customer-42", "orderId": "%s", "paymentMethod": "%s",
         "amount": {"amountMinor": 79900, "currency": "EUR"}}
        """
        .formatted(orderId, paymentMethod);
  }

  private RestTestClient.ResponseSpec orchestrationAuthorizes(String key, String body) {
    return http.post()
        .uri("/payments")
        .headers(
            h -> {
              h.setBearerAuth(ORCHESTRATION);
              if (key != null) {
                h.set("Idempotency-Key", key);
              }
            })
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  private RestTestClient.ResponseSpec orchestrationVoids(String id, String customerId) {
    return http.post()
        .uri("/payments/{id}/void", id)
        .headers(h -> h.setBearerAuth(ORCHESTRATION))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"customerId\": \"%s\"}".formatted(customerId))
        .exchange();
  }

  private RestTestClient.ResponseSpec orchestrationReads(String id, String customerId) {
    return http.get()
        .uri("/payments/{id}?customerId={customerId}", id, customerId)
        .headers(h -> h.setBearerAuth(ORCHESTRATION))
        .exchange();
  }
}
