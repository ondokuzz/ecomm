package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The mock gateway as the stack runs it: a second after answering a bank-confirmed card as pending,
 * it signs its webhook and posts it to Payment's own endpoint, which settles the Payment. Unlike
 * {@link PaymentApiTest}'s, nothing stands between the two.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class MockGatewayWebhooksApiTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    PaymentApiTest.infrastructure(registry);
    registry.add("payment.mock-gateway.webhook-delay", () -> "1s");
  }

  @Autowired RestTestClient http;

  @Test
  void theBankConfirmingSettlesThePaymentOnItsOwn() {
    var payment = authorize("order-mock-delivery", "tok_async_approve");
    assertThat(payment.status()).isEqualTo("PENDING");

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(staffPayments("order-mock-delivery").getFirst().status())
                    .isEqualTo("AUTHORIZED"));
  }

  @Test
  void theBankDecliningSettlesThePaymentOnItsOwn() {
    authorize("order-mock-delivery-declined", "tok_async_decline");

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(staffPayments("order-mock-delivery-declined").getFirst())
                    .satisfies(
                        p -> {
                          assertThat(p.status()).isEqualTo("DECLINED");
                          assertThat(p.declineReason()).isEqualTo("card_declined");
                        }));
  }

  private PaymentApiTest.PaymentView authorize(String orderId, String paymentMethod) {
    return http.post()
        .uri("/payments")
        .headers(h -> h.setBearerAuth(PaymentApiTest.orchestrationToken()))
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"customerId": "customer-42", "orderId": "%s", "paymentMethod": "%s",
             "amount": {"amountMinor": 79900, "currency": "EUR"}}
            """
                .formatted(orderId, paymentMethod))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(PaymentApiTest.PaymentView.class)
        .returnResult()
        .getResponseBody();
  }

  private List<PaymentApiTest.PaymentView> staffPayments(String orderId) {
    return http.get()
        .uri("/staff/payments?orderId={orderId}", orderId)
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("staff-1", "STAFF")))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<List<PaymentApiTest.PaymentView>>() {})
        .returnResult()
        .getResponseBody();
  }
}
