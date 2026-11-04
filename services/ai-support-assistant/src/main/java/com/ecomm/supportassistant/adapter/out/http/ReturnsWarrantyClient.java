package com.ecomm.supportassistant.adapter.out.http;

import com.ecomm.supportassistant.adapter.out.http.ContextReads.MoneyBody;
import com.ecomm.supportassistant.application.port.out.Lookup;
import com.ecomm.supportassistant.application.port.out.ReturnsWarrantyPort;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Returns & Warranty's Customer reads, {@code GET /rmas}, by RMA reference and by Order too, {@code
 * GET /rmas/{id}} and {@code GET /rmas/eligibility?orderId=}, with the Customer's own token: its
 * ownership check answers another Customer's RMA, or Order, as a 404, and leaves it out of a list.
 */
@Component
class ReturnsWarrantyClient implements ReturnsWarrantyPort {

  private static final String CONTEXT = "Returns & Warranty";

  private final RestClient http;

  ReturnsWarrantyClient(@Qualifier("returnsWarrantyRestClient") RestClient http) {
    this.http = http;
  }

  private record RmaBody(
      String id,
      String reference,
      String orderId,
      String productName,
      int quantity,
      String kind,
      String reason,
      WindowBody window,
      MoneyBody refundAmount,
      String status,
      Instant requestedAt,
      Instant sendBackBy,
      String warehouseId,
      InspectionBody inspection,
      ResolutionBody resolution,
      List<StepBody> history) {

    RmaView toView() {
      return new RmaView(
          id,
          reference,
          orderId,
          productName,
          quantity,
          kind,
          reason,
          window.endsAt(),
          refundAmount == null ? null : refundAmount.toMoney(),
          status,
          requestedAt,
          sendBackBy,
          warehouseId,
          inspection == null ? null : new Inspection(inspection.outcome(), inspection.note()),
          resolution == null ? null : resolution.paymentTransactionReference(),
          history == null
              ? List.of()
              : history.stream().map(s -> new Step(s.status(), s.at(), s.note())).toList());
    }
  }

  private record WindowBody(Instant endsAt) {}

  private record InspectionBody(String outcome, String note) {}

  private record ResolutionBody(String paymentTransactionReference) {}

  private record StepBody(String status, Instant at, String note) {}

  private record PageBody(List<RmaBody> items) {}

  private record EligibilityBody(
      String variantId,
      String productName,
      Instant returnPeriodEndsAt,
      Instant warrantyWindowEndsAt,
      int quantity) {}

  private record EligibilitiesBody(List<EligibilityBody> lines) {}

  @Override
  public Lookup<List<RmaView>> rmas(String reference, String orderId, int limit) {
    return ContextReads.lookup(
        CONTEXT,
        http.get()
            .uri(
                uri -> {
                  uri.path("/rmas");
                  if (reference != null) {
                    uri.queryParam("reference", reference);
                  }
                  if (orderId != null) {
                    uri.queryParam("orderId", orderId);
                  }
                  return uri.queryParam("page", 0).queryParam("size", limit).build();
                }),
        new ParameterizedTypeReference<PageBody>() {},
        page -> page.items().stream().map(RmaBody::toView).toList());
  }

  @Override
  public Lookup<RmaView> rma(String rmaId) {
    return ContextReads.lookup(
        CONTEXT,
        http.get().uri("/rmas/{id}", rmaId),
        new ParameterizedTypeReference<RmaBody>() {},
        RmaBody::toView);
  }

  @Override
  public Lookup<List<Eligibility>> eligibility(String orderId) {
    return ContextReads.lookup(
        CONTEXT,
        http.get().uri("/rmas/eligibility?orderId={orderId}", orderId),
        new ParameterizedTypeReference<EligibilitiesBody>() {},
        body ->
            body.lines().stream()
                .map(
                    l ->
                        new Eligibility(
                            l.variantId(),
                            l.productName(),
                            l.returnPeriodEndsAt(),
                            l.warrantyWindowEndsAt(),
                            l.quantity()))
                .toList());
  }
}
