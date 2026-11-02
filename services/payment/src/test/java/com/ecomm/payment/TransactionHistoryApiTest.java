package com.ecomm.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * A Customer reads their Payment transaction history: every Payment transaction on any of their
 * Payments, newest first, each with its Payment and its Order, in one call. Each test pays as a
 * Customer of its own, since every test class shares one database.
 */
class TransactionHistoryApiTest extends PaymentApiTest {

  @Test
  void aCustomerReadsTheirTransactionsAcrossTheirPaymentsNewestFirst() {
    var customer = newCustomer();
    var first = "3f2a9c1b-0000-4000-8000-000000000001";
    var second = "9b1d4e7a-0000-4000-8000-000000000002";
    var firstPayment = authorizedFor(customer, first, "tok_approve");
    var secondPayment = authorizedFor(customer, second, "tok_approve");
    capture(firstPayment.id(), customer).expectStatus().isOk();

    var history = history(customer, "");

    assertThat(history.items())
        .extracting(
            LineView::kind, LineView::paymentId, LineView::orderId, LineView::orderReference)
        .containsExactly(
            tuple("CAPTURE", firstPayment.id(), first, "#3F2A9C1B"),
            tuple("AUTHORIZATION", secondPayment.id(), second, "#9B1D4E7A"),
            tuple("AUTHORIZATION", firstPayment.id(), first, "#3F2A9C1B"));
    assertThat(history.items())
        .extracting(LineView::paymentStatus)
        .containsExactly("CAPTURED", "AUTHORIZED", "CAPTURED");
    var capture = history.items().getFirst();
    assertThat(capture.amount()).isEqualTo(new AmountView(79900, "EUR"));
    assertThat(capture.outcome()).isEqualTo("APPROVED");
    assertThat(capture.declineReason()).isNull();
    assertThat(capture.reference()).isNull();
    assertThat(capture.at()).isNotBlank();
    assertThat(history.total()).isEqualTo(3);
    assertThat(history.page()).isZero();
    assertThat(history.size()).isEqualTo(20);
  }

  @Test
  void anotherCustomersTransactionsNeverAppear() {
    var customer = newCustomer();
    var someoneElse = newCustomer();
    var own = authorizedFor(customer, "a1000000-0000-4000-8000-000000000001", "tok_approve");
    authorizedFor(someoneElse, "a2000000-0000-4000-8000-000000000002", "tok_approve");

    assertThat(history(customer, "").items())
        .extracting(LineView::paymentId)
        .containsExactly(own.id());
    assertThat(history(customer, "customerId=" + someoneElse).items())
        .extracting(LineView::paymentId)
        .containsExactly(own.id());
  }

  @Test
  void aCustomerWithoutPaymentsHasNoTransactions() {
    var history = history(newCustomer(), "");

    assertThat(history.items()).isEmpty();
    assertThat(history.total()).isZero();
  }

  @Test
  void everyKindOfTransactionIsInTheHistory() {
    var customer = newCustomer();
    var voided = authorizedFor(customer, "b1000000-0000-4000-8000-000000000001", "tok_approve");
    voidPayment(voided.id(), customer).expectStatus().isOk();
    authorize(
            """
            {"customerId": "%s", "orderId": "b2000000-0000-4000-8000-000000000002",
             "paymentMethod": "tok_decline", "amount": {"amountMinor": 79900, "currency": "EUR"}}
            """
                .formatted(customer))
        .expectStatus()
        .isCreated();
    var refused =
        authorizedFor(customer, "b3000000-0000-4000-8000-000000000003", "tok_capture_decline");
    capture(refused.id(), customer).expectStatus().isEqualTo(402);
    var refunded = authorizedFor(customer, "b4000000-0000-4000-8000-000000000004", "tok_approve");
    capture(refunded.id(), customer).expectStatus().isOk();
    refundFor(customer, refunded.id(), 30000, "RMA-B4000000").expectStatus().isOk();
    var refundRefused =
        authorizedFor(customer, "b5000000-0000-4000-8000-000000000005", "tok_refund_decline");
    capture(refundRefused.id(), customer).expectStatus().isOk();
    refundFor(customer, refundRefused.id(), 79900, "RMA-B5000000").expectStatus().isEqualTo(402);

    var history = history(customer, "size=100");

    assertThat(history.items())
        .extracting(
            LineView::kind,
            LineView::outcome,
            LineView::declineReason,
            LineView::reference,
            LineView::amount,
            LineView::paymentStatus,
            LineView::orderReference)
        .containsExactly(
            tuple(
                "REFUND",
                "DECLINED",
                "refund_window_closed",
                "RMA-B5000000",
                new AmountView(79900, "EUR"),
                "CAPTURED",
                "#B5000000"),
            tuple(
                "CAPTURE",
                "APPROVED",
                null,
                null,
                new AmountView(79900, "EUR"),
                "CAPTURED",
                "#B5000000"),
            tuple(
                "AUTHORIZATION",
                "APPROVED",
                null,
                null,
                new AmountView(79900, "EUR"),
                "CAPTURED",
                "#B5000000"),
            tuple(
                "REFUND",
                "APPROVED",
                null,
                "RMA-B4000000",
                new AmountView(30000, "EUR"),
                "PARTIALLY_REFUNDED",
                "#B4000000"),
            tuple(
                "CAPTURE",
                "APPROVED",
                null,
                null,
                new AmountView(79900, "EUR"),
                "PARTIALLY_REFUNDED",
                "#B4000000"),
            tuple(
                "AUTHORIZATION",
                "APPROVED",
                null,
                null,
                new AmountView(79900, "EUR"),
                "PARTIALLY_REFUNDED",
                "#B4000000"),
            tuple(
                "CAPTURE",
                "DECLINED",
                "authorization_expired",
                null,
                new AmountView(79900, "EUR"),
                "AUTHORIZED",
                "#B3000000"),
            tuple(
                "AUTHORIZATION",
                "APPROVED",
                null,
                null,
                new AmountView(79900, "EUR"),
                "AUTHORIZED",
                "#B3000000"),
            tuple(
                "AUTHORIZATION",
                "DECLINED",
                "card_declined",
                null,
                new AmountView(79900, "EUR"),
                "DECLINED",
                "#B2000000"),
            tuple(
                "VOID",
                "APPROVED",
                null,
                null,
                new AmountView(79900, "EUR"),
                "VOIDED",
                "#B1000000"),
            tuple(
                "AUTHORIZATION",
                "APPROVED",
                null,
                null,
                new AmountView(79900, "EUR"),
                "VOIDED",
                "#B1000000"));
  }

  @Test
  void theHistoryIsPaged() {
    var customer = newCustomer();
    var first = authorizedFor(customer, "c1000000-0000-4000-8000-000000000001", "tok_approve");
    var second = authorizedFor(customer, "c2000000-0000-4000-8000-000000000002", "tok_approve");
    var third = authorizedFor(customer, "c3000000-0000-4000-8000-000000000003", "tok_approve");

    var firstPage = history(customer, "page=0&size=2");
    var secondPage = history(customer, "page=1&size=2");

    assertThat(firstPage.items())
        .extracting(LineView::paymentId)
        .containsExactly(third.id(), second.id());
    assertThat(secondPage.items()).extracting(LineView::paymentId).containsExactly(first.id());
    assertThat(List.of(firstPage.page(), firstPage.size(), secondPage.page(), secondPage.size()))
        .containsExactly(0, 2, 1, 2);
    assertThat(List.of(firstPage.total(), secondPage.total())).containsExactly(3L, 3L);
    assertThat(history(customer, "page=2&size=2").items()).isEmpty();
    assertThat(history(customer, "size=100").items()).hasSize(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"page=-1", "size=0", "size=101", "from=yesterday", "to=2026-11-02"})
  void aPageOrBoundThatCantBeIsRefused(String query) {
    historyRequest(tokenOf(newCustomer()), query)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void fromAndToNarrowItByWhenEachHappened() {
    var customer = newCustomer();
    var first = authorizedFor(customer, "d1000000-0000-4000-8000-000000000001", "tok_approve");
    var middle = authorizedFor(customer, "d2000000-0000-4000-8000-000000000002", "tok_approve");
    var last = authorizedFor(customer, "d3000000-0000-4000-8000-000000000003", "tok_approve");
    var middleAt = middle.transactions().getFirst().at();
    var lastAt = last.transactions().getFirst().at();

    assertThat(history(customer, "from=" + middleAt).items())
        .extracting(LineView::paymentId)
        .containsExactly(last.id(), middle.id());
    assertThat(history(customer, "to=" + lastAt).items())
        .extracting(LineView::paymentId)
        .containsExactly(middle.id(), first.id());
    var between = history(customer, "from=" + middleAt + "&to=" + lastAt);
    assertThat(between.items()).extracting(LineView::paymentId).containsExactly(middle.id());
    assertThat(between.total()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"STAFF", "ORCHESTRATION", "CHECKOUT"})
  void onlyACustomerReadsTheirHistory(String role) {
    historyRequest(FakeKeycloak.token("someone-" + role, role), "").expectStatus().isForbidden();
  }

  @Test
  void readingTheHistoryNeedsAToken() {
    http.get().uri("/payments/transactions").exchange().expectStatus().isUnauthorized();
  }

  /** Refunds {@code amountMinor} EUR of the Payment for {@code customer}, as the returns Saga. */
  RestTestClient.ResponseSpec refundFor(
      String customer, String paymentId, long amountMinor, String reference) {
    return refundWithKey(
        paymentId,
        UUID.randomUUID().toString(),
        refundBody(customer, amountMinor, "EUR", reference));
  }

  /** A Customer of this test's own, so that no other test's Payments are theirs. */
  static String newCustomer() {
    return "customer-" + UUID.randomUUID();
  }

  /** The {@code CUSTOMER} token of {@code customer}. */
  static String tokenOf(String customer) {
    return FakeKeycloak.token(customer, "CUSTOMER");
  }

  /** A Payment of {@code orderId} for {@code customer}, paid with {@code paymentMethod}. */
  PaymentView authorizedFor(String customer, String orderId, String paymentMethod) {
    return authorize(
            """
            {"customerId": "%s", "orderId": "%s", "paymentMethod": "%s",
             "amount": {"amountMinor": 79900, "currency": "EUR"}}
            """
                .formatted(customer, orderId, paymentMethod))
        .expectStatus()
        .isCreated()
        .expectBody(PaymentView.class)
        .returnResult()
        .getResponseBody();
  }

  /** The Customer's history, as they read it, with {@code query} after {@code ?}. */
  HistoryView history(String customer, String query) {
    return historyRequest(tokenOf(customer), query)
        .expectStatus()
        .isOk()
        .expectBody(HistoryView.class)
        .returnResult()
        .getResponseBody();
  }

  /** Reads the history with {@code token}, with {@code query} after {@code ?}. */
  RestTestClient.ResponseSpec historyRequest(String token, String query) {
    return http.get()
        .uri("/payments/transactions?" + query)
        .headers(h -> h.setBearerAuth(token))
        .exchange();
  }

  /** A page of the history, as a client reads it. */
  record HistoryView(List<LineView> items, int page, int size, long total) {}

  /** One line of the history, independent of the service's classes. */
  record LineView(
      String kind,
      AmountView amount,
      String outcome,
      String declineReason,
      String at,
      String reference,
      String paymentId,
      String paymentStatus,
      String orderId,
      String orderReference) {}
}
