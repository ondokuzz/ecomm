package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import com.ecomm.orchestration.application.port.out.RefundActivities;
import com.ecomm.orchestration.application.port.out.Refusals;
import io.temporal.failure.ApplicationFailure;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Payment's read by Order, which finds the Payment to refund, and its {@code
 * /payments/{id}/refunds}. A 402 ({@code refundDeclined}) is the gateway's answer, not a failure; a
 * 502 is retried like any other failure, and a 409 or 422 is a refusal.
 */
@Component("refundActivities")
class RefundHttpActivities implements RefundActivities {

  /** The statuses of a Payment a Refund can be made of, or was made of already. */
  private static final Set<String> REFUNDABLE =
      Set.of("CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED");

  private final RestClient http;

  RefundHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.paymentUrl());
  }

  private record TransactionBody(
      String kind, String outcome, String gatewayReference, String reference) {}

  private record PaymentBody(String id, String status, List<TransactionBody> transactions) {

    /** The gateway reference of an approved Refund of it for {@code reference}, if it has one. */
    Optional<String> refundFor(String reference) {
      return (transactions == null ? List.<TransactionBody>of() : transactions)
          .stream()
              .filter(
                  t ->
                      "REFUND".equals(t.kind())
                          && "APPROVED".equals(t.outcome())
                          && reference.equals(t.reference()))
              .map(TransactionBody::gatewayReference)
              .filter(Objects::nonNull)
              .reduce((first, last) -> last);
    }
  }

  private record RefundBody(String customerId, Amount amount, String reference) {}

  private record DeclineBody(String declineReason) {}

  @Override
  public PaymentToRefund findRefundablePayment(
      String customerId, String orderId, String reference) {
    var payments =
        Steps.call(
            "findRefundablePayment",
            () ->
                http.get()
                    .uri(
                        "/payments/by-order?orderId={orderId}&customerId={customerId}",
                        orderId,
                        customerId)
                    .retrieve()
                    .body(PaymentBody[].class));
    var all = Arrays.asList(payments == null ? new PaymentBody[0] : payments);
    return all.stream()
        .filter(p -> p.refundFor(reference).isPresent())
        .findFirst()
        .map(p -> new PaymentToRefund(p.id(), p.refundFor(reference).orElseThrow()))
        .or(
            () ->
                all.stream()
                    .filter(p -> REFUNDABLE.contains(p.status()))
                    .findFirst()
                    .map(p -> new PaymentToRefund(p.id(), null)))
        .orElseThrow(
            () -> {
              Steps.countFailure("findRefundablePayment");
              return ApplicationFailure.newNonRetryableFailure(
                  "Order " + orderId + " has no captured Payment to refund", Refusals.REFUSED);
            });
  }

  @Override
  public Refund refundPayment(
      String customerId, String paymentId, Amount amount, String reference) {
    return Steps.call(
        "refundPayment",
        () -> {
          try {
            var payment =
                http.post()
                    .uri("/payments/{id}/refunds", paymentId)
                    .header("Idempotency-Key", Steps.idempotencyKey("refundPayment"))
                    .body(new RefundBody(customerId, amount, reference))
                    .retrieve()
                    .body(PaymentBody.class);
            var refund =
                Optional.ofNullable(payment)
                    .flatMap(p -> p.refundFor(reference))
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Payment answered Refund for "
                                    + reference
                                    + " without its approved transaction"));
            return new Refund(Refund.Status.REFUNDED, refund, null);
          } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() != HttpStatus.PAYMENT_REQUIRED.value()) {
              throw e;
            }
            var decline = e.getResponseBodyAs(DeclineBody.class);
            return new Refund(
                Refund.Status.DECLINED, null, decline == null ? null : decline.declineReason());
          }
        });
  }
}
