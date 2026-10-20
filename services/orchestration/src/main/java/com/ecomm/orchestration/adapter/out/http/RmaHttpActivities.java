package com.ecomm.orchestration.adapter.out.http;

import com.ecomm.orchestration.application.port.in.CheckoutRequest.Amount;
import com.ecomm.orchestration.application.port.out.RmaActivities;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Returns & Warranty's {@code /rmas/{id}} and its commands for the returns Saga. A 409 on {@code
 * expire} says the units were received before it ({@code transitionRefused}): an answer, not a
 * failure.
 */
@Component("rmaActivities")
class RmaHttpActivities implements RmaActivities {

  private final RestClient http;

  RmaHttpActivities(ServiceRestClients clients, ServiceUrls urls) {
    this.http = clients.forService(urls.returnsWarrantyUrl());
  }

  private record InspectionBody(String outcome, String note) {}

  private record RmaBody(
      String status, String sendBackBy, Amount refundAmount, InspectionBody inspection) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record ResolveBody(
      String resolution,
      String paymentTransactionReference,
      String replacementShipmentId,
      String note) {}

  private record ResolvedBody(boolean orderFullyRefunded) {}

  private record HoldBody(String reason) {}

  @Override
  public Rma findRma(String rmaId) {
    var rma =
        Steps.call(
            "findRma", () -> http.get().uri("/rmas/{id}", rmaId).retrieve().body(RmaBody.class));
    if (rma == null || rma.status() == null) {
      throw new IllegalStateException(
          "Returns & Warranty answered RMA " + rmaId + " without a status");
    }
    var inspection = rma.inspection();
    return new Rma(
        rma.status(),
        rma.sendBackBy(),
        rma.refundAmount(),
        inspection == null ? null : inspection.outcome(),
        inspection == null ? null : inspection.note());
  }

  @Override
  public Expiry expireRma(String rmaId) {
    return Steps.call(
        "expireRma",
        () -> {
          try {
            http.post()
                .uri("/rmas/{id}/expire", rmaId)
                .header("Idempotency-Key", Steps.idempotencyKey("expireRma"))
                .retrieve()
                .toBodilessEntity();
            return Expiry.EXPIRED;
          } catch (HttpClientErrorException.Conflict e) {
            return Expiry.RECEIVED_MEANWHILE;
          }
        });
  }

  @Override
  public boolean resolveRma(
      String rmaId,
      String resolution,
      String paymentTransactionReference,
      String replacementShipmentId,
      String note) {
    var resolved =
        Steps.call(
            "resolveRma",
            () ->
                http.post()
                    .uri("/rmas/{id}/resolve", rmaId)
                    .header("Idempotency-Key", Steps.idempotencyKey("resolveRma"))
                    .body(
                        new ResolveBody(
                            resolution, paymentTransactionReference, replacementShipmentId, note))
                    .retrieve()
                    .body(ResolvedBody.class));
    return resolved != null && resolved.orderFullyRefunded();
  }

  @Override
  public void holdRma(String rmaId, String reason) {
    Steps.run(
        "holdRma",
        () ->
            http.post()
                .uri("/rmas/{id}/hold", rmaId)
                .header("Idempotency-Key", Steps.idempotencyKey("holdRma"))
                .body(new HoldBody(reason))
                .retrieve()
                .toBodilessEntity());
  }
}
