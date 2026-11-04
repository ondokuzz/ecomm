package com.ecomm.supportassistant.adapter.out.http;

import com.ecomm.supportassistant.adapter.out.http.ContextReads.MoneyBody;
import com.ecomm.supportassistant.application.port.out.Lookup;
import com.ecomm.supportassistant.application.port.out.PaymentPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Payment's Customer reads, {@code GET /payments?orderId=} and {@code GET /payments/transactions},
 * with the Customer's own token: Payment answers only the token's Customer's Payments, and none for
 * another Customer's Order.
 */
@Component
class PaymentClient implements PaymentPort {

  private final RestClient http;

  PaymentClient(@Qualifier("paymentRestClient") RestClient http) {
    this.http = http;
  }

  private record PaymentBody(
      String id,
      MoneyBody amount,
      String status,
      String declineReason,
      List<TransactionBody> transactions) {

    PaymentView toView() {
      return new PaymentView(
          id,
          status,
          amount.toMoney(),
          declineReason,
          transactions == null
              ? List.of()
              : transactions.stream().map(TransactionBody::toTransaction).toList());
    }
  }

  private record TransactionBody(
      String kind,
      MoneyBody amount,
      String outcome,
      String declineReason,
      Instant at,
      String reference) {

    PaymentTransaction toTransaction() {
      return new PaymentTransaction(kind, amount.toMoney(), outcome, declineReason, at, reference);
    }
  }

  private record HistoryLineBody(
      String kind,
      MoneyBody amount,
      String outcome,
      String declineReason,
      Instant at,
      String reference,
      String paymentId,
      String paymentStatus,
      String orderId) {

    HistoryLine toLine() {
      return new HistoryLine(
          new PaymentTransaction(kind, amount.toMoney(), outcome, declineReason, at, reference),
          paymentId,
          paymentStatus,
          orderId);
    }
  }

  private record HistoryBody(List<HistoryLineBody> items, long total) {}

  @Override
  public Lookup<List<PaymentView>> payments(String orderId) {
    return ContextReads.lookup(
        "Payment",
        http.get().uri("/payments?orderId={orderId}", orderId),
        new ParameterizedTypeReference<List<PaymentBody>>() {},
        payments -> payments.stream().map(PaymentBody::toView).toList());
  }

  @Override
  public Lookup<History> transactionHistory(Instant from, Instant to, int limit) {
    return ContextReads.lookup(
        "Payment",
        http.get()
            .uri(
                uri ->
                    uri.path("/payments/transactions")
                        .queryParamIfPresent("from", Optional.ofNullable(from))
                        .queryParamIfPresent("to", Optional.ofNullable(to))
                        .queryParam("page", 0)
                        .queryParam("size", limit)
                        .build()),
        new ParameterizedTypeReference<HistoryBody>() {},
        history ->
            new History(
                history.items().stream().map(HistoryLineBody::toLine).toList(), history.total()));
  }
}
