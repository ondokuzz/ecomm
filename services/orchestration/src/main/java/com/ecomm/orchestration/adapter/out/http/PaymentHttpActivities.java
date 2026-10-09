package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import com.ecomm.orchestration.application.port.out.PaymentActivities;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Payment's {@code /payments} commands. A declined Payment is recorded too, as a 201 with status
 * {@code DECLINED}; a gateway that fails to answer is a 502, retried like any other failure.
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
    return new Authorization(
        payment.id(), Authorization.Status.valueOf(payment.status()), payment.declineReason());
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
