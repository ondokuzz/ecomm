package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A Customer reads their Payments for one of their Orders, newest first, and nothing of anyone
 * else's; Staff read every Payment for an Order, with its transactions.
 */
class PaymentsOfAnOrderApiTest extends PaymentApiTest {

  @Test
  void aCustomerReadsTheirPaymentsForAnOrderNewestFirst() {
    var declined =
        authorize("order-paid-again", "tok_decline")
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();
    var authorized = authorized("order-paid-again");

    var payments =
        customerPayments(customerToken(), "order-paid-again")
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<PaymentView>>() {})
            .returnResult()
            .getResponseBody();

    assertThat(payments).containsExactly(authorized, declined);
  }

  @Test
  void anotherCustomersOrderHasNoPaymentsForThem() {
    authorized("order-someone-elses");

    customerPayments(FakeKeycloak.token("customer-7", "CUSTOMER"), "order-someone-elses")
        .expectStatus()
        .isOk()
        .expectBody()
        .json("[]");
  }

  @Test
  void anOrderWithoutPaymentsHasNone() {
    customerPayments(customerToken(), "order-unpaid").expectStatus().isOk().expectBody().json("[]");
  }

  @Test
  void staffReadEveryPaymentForAnOrderWithItsTransactions() {
    var payment = authorized("order-for-staff");
    var voided =
        voidPayment(payment.id(), "customer-42")
            .expectBody(PaymentView.class)
            .returnResult()
            .getResponseBody();

    assertThat(staffPayments("order-for-staff")).containsExactly(voided);
    assertThat(voided.transactions())
        .extracting(TransactionView::kind)
        .containsExactly("AUTHORIZATION", "VOID");
  }

  @Test
  void theOrderIsRequired() {
    http.get()
        .uri("/payments")
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    http.get()
        .uri("/staff/payments")
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "CHECKOUT"})
  void onlyStaffReadEveryPaymentForAnOrder(String role) {
    http.get()
        .uri("/staff/payments?orderId=order-for-staff")
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("someone-" + role, role)))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void readingAnOrdersPaymentsNeedsAToken() {
    http.get().uri("/payments?orderId=order-1").exchange().expectStatus().isUnauthorized();
    http.get().uri("/staff/payments?orderId=order-1").exchange().expectStatus().isUnauthorized();
  }

  private RestTestClient.ResponseSpec customerPayments(String token, String orderId) {
    return http.get()
        .uri("/payments?orderId={orderId}", orderId)
        .headers(h -> h.setBearerAuth(token))
        .exchange();
  }
}
