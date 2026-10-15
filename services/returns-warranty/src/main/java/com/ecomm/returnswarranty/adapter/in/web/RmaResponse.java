package com.ecomm.returnswarranty.adapter.in.web;

import com.ecomm.returnswarranty.domain.Rma;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * An RMA as clients read it: its reference, what it claims, the window it was checked against, its
 * status and its decision history, oldest first. A null field is left out.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record RmaResponse(
    String id,
    String reference,
    String orderId,
    String customerId,
    String variantId,
    String productName,
    int quantity,
    List<String> serialNumbers,
    String kind,
    String reason,
    WindowResponse window,
    String status,
    String requestedAt,
    List<DecisionResponse> decisions) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record WindowResponse(String kind, String startsAt, String endsAt, WarrantyResponse warranty) {}

  record WarrantyResponse(int months, String manufacturer) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record DecisionResponse(String status, String decidedBy, String decidedAt, String note) {}

  static RmaResponse of(Rma rma) {
    var window = rma.window();
    var warranty = window.warranty();
    return new RmaResponse(
        rma.id().toString(),
        rma.reference(),
        rma.orderId(),
        rma.customerId(),
        rma.variantId(),
        rma.productName(),
        rma.quantity(),
        rma.serialNumbers(),
        rma.kind().name(),
        rma.reason(),
        new WindowResponse(
            window.kind().name(),
            window.startsAt().toString(),
            window.endsAt().toString(),
            warranty == null
                ? null
                : new WarrantyResponse(warranty.months(), warranty.manufacturer())),
        rma.status().name(),
        rma.requestedAt().toString(),
        rma.decisions().stream()
            .map(
                d ->
                    new DecisionResponse(
                        d.status().name(), d.decidedBy(), d.decidedAt().toString(), d.note()))
            .toList());
  }
}
