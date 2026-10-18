package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.out.CaptureActivities;
import com.ecomm.orchestration.application.port.out.Refusals;
import io.temporal.failure.ApplicationFailure;
import java.util.Arrays;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Payment's read by Order, which finds the Payment authorized, or captured already, and its {@code
 * /payments/{id}/capture}. A 402 ({@code captureDeclined}) is the gateway's answer, not a failure;
 * a 502 is retried like any other failure, and a 409 ({@code paymentNotCapturable}) is a refusal.
 */
@Component("captureActivities")
class CaptureHttpActivities implements CaptureActivities {

  /** The statuses of a Payment the capture can be made on, or was made on already. */
  private static final Set<String> CAPTURABLE = Set.of("AUTHORIZED", "CAPTURED");

  private final RestClient http;

  CaptureHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.paymentUrl());
  }

  private record PaymentBody(String id, String status) {}

  private record CustomerBody(String customerId) {}

  private record DeclineBody(String declineReason) {}

  @Override
  public PaymentToCapture findPayment(String customerId, String orderId) {
    var payments =
        Steps.call(
            "findPayment",
            () ->
                http.get()
                    .uri("/payments?orderId={orderId}&customerId={customerId}", orderId, customerId)
                    .retrieve()
                    .body(PaymentBody[].class));
    return Arrays.stream(payments == null ? new PaymentBody[0] : payments)
        .filter(p -> CAPTURABLE.contains(p.status()))
        .findFirst()
        .map(p -> new PaymentToCapture(p.id(), "CAPTURED".equals(p.status())))
        .orElseThrow(
            () -> {
              Steps.countFailure("findPayment");
              return ApplicationFailure.newNonRetryableFailure(
                  "Order " + orderId + " has no authorized Payment to capture", Refusals.REFUSED);
            });
  }

  @Override
  public Capture capturePayment(String customerId, String paymentId) {
    return Steps.call(
        "capturePayment",
        () -> {
          try {
            http.post()
                .uri("/payments/{id}/capture", paymentId)
                .header("Idempotency-Key", Steps.idempotencyKey("capturePayment"))
                .body(new CustomerBody(customerId))
                .retrieve()
                .toBodilessEntity();
            return new Capture(paymentId, Capture.Status.CAPTURED, null);
          } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() != HttpStatus.PAYMENT_REQUIRED.value()) {
              throw e;
            }
            var decline = e.getResponseBodyAs(DeclineBody.class);
            return new Capture(
                paymentId,
                Capture.Status.DECLINED,
                decline == null ? null : decline.declineReason());
          }
        });
  }
}
