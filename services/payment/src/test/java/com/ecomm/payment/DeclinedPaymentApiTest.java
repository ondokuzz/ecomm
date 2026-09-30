package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The mock gateway reads the payment method as a test token: {@code tok_approve} authorizes, and
 * every other token declines, with a reason. A decline is recorded like an authorization.
 */
class DeclinedPaymentApiTest extends PaymentApiTest {

  @Test
  void theApproveTokenAuthorizes() {
    authorize("order-approve", "tok_approve")
        .expectStatus()
        .isCreated()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("AUTHORIZED")
        .jsonPath("$.declineReason")
        .isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "tok_decline, card_declined",
    "tok_insufficient_funds, insufficient_funds",
    "tok_visa, unknown_payment_method",
    "TOK_APPROVE, unknown_payment_method"
  })
  void aDeclineIsRecordedWithItsReason(String paymentMethod, String reason) {
    authorize("order-" + paymentMethod, paymentMethod)
        .expectStatus()
        .isCreated()
        .expectHeader()
        .exists("Location")
        .expectBody()
        .jsonPath("$.id")
        .isNotEmpty()
        .jsonPath("$.orderId")
        .isEqualTo("order-" + paymentMethod)
        .jsonPath("$.amount.amountMinor")
        .isEqualTo(79900)
        .jsonPath("$.status")
        .isEqualTo("DECLINED")
        .jsonPath("$.declineReason")
        .isEqualTo(reason)
        .jsonPath("$.gatewayReference")
        .isNotEmpty();
  }

  @Test
  void aDeclinedPaymentCanBeReadBackByItsOwner() {
    var created =
        authorize("order-declined-read", "tok_insufficient_funds")
            .expectStatus()
            .isCreated()
            .expectBody(PaymentView.class)
            .returnResult();
    var payment = created.getResponseBody();

    http.get()
        .uri(created.getResponseHeaders().getLocation())
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(PaymentView.class)
        .isEqualTo(payment);
    assertThat(payment.status()).isEqualTo("DECLINED");
    assertThat(payment.declineReason()).isEqualTo("insufficient_funds");
  }
}
