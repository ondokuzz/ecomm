package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import com.ecomm.orchestration.application.port.out.PaymentActivities;
import com.ecomm.orchestration.application.port.out.Refusals;
import io.temporal.failure.ApplicationFailure;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Payment's {@code /payments} commands, and its read of one Payment. A declined Payment is recorded
 * too, as a 201 with status {@code DECLINED}, and a pending one as a 201 with status {@code
 * PENDING}; a gateway that fails to answer is a 502, retried like any other failure.
 */
@Component("paymentActivities")
class PaymentHttpActivities implements PaymentActivities {

  private final RestClient http;

  PaymentHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.paymentUrl());
  }

  private record AuthorizeBody(
      String customerId, String orderId, String paymentMethod, Amount amount) {}

  private record PaymentBody(String id, String status, String declineReason) {}

  private record CustomerBody(String customerId) {}

  @Override
  public Authorization authorizePayment(
      String customerId, String orderId, Amount amount, String paymentMethod) {
    var payment =
        Steps.call(
            "authorizePayment",
            () ->
                http.post()
                    .uri("/payments")
                    .header("Idempotency-Key", Steps.idempotencyKey("authorizePayment"))
                    .body(new AuthorizeBody(customerId, orderId, paymentMethod, amount))
                    .retrieve()
                    .body(PaymentBody.class));
    if (payment == null || payment.id() == null || payment.status() == null) {
      throw new IllegalStateException(
          "Payment answered for Order " + orderId + " without a status");
    }
    return authorizationOf(payment);
  }

  private static Authorization authorizationOf(PaymentBody payment) {
    return new Authorization(
        payment.id(), Authorization.Status.valueOf(payment.status()), payment.declineReason());
  }

  /**
   * A Payment still pending fails the activity as {@link #SETTLEMENT_PENDING}, outside the step's
   * failure count, since waiting isn't failing; anything but authorized or declined fails it for
   * good.
   */
  @Override
  public Authorization awaitSettlement(String customerId, String paymentId) {
    var payment =
        Steps.call(
            "awaitSettlement",
            () ->
                http.get()
                    .uri("/payments/{id}?customerId={customerId}", paymentId, customerId)
                    .retrieve()
                    .body(PaymentBody.class));
    if (payment == null || payment.status() == null) {
      throw new IllegalStateException("Payment " + paymentId + " answered without a status");
    }
    return switch (payment.status()) {
      case "AUTHORIZED", "DECLINED" -> authorizationOf(payment);
      case "PENDING" ->
          throw ApplicationFailure.newFailure(
              "Payment " + paymentId + " is still pending", SETTLEMENT_PENDING);
      default ->
          throw ApplicationFailure.newNonRetryableFailure(
              "Payment " + paymentId + " is " + payment.status() + ", not settled",
              Refusals.REFUSED);
    };
  }

  @Override
  public void voidPayment(String customerId, String paymentId) {
    Steps.run(
        "voidPayment",
        () ->
            http.post()
                .uri("/payments/{id}/void", paymentId)
                .body(new CustomerBody(customerId))
                .retrieve()
                .toBodilessEntity());
  }
}
