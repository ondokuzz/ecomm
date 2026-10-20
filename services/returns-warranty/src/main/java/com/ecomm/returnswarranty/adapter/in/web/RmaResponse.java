package com.ecomm.returnswarranty.adapter.in.web;

import com.ecomm.returnswarranty.domain.Rma;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * An RMA as clients read it: its reference, what it claims, the window it was checked against, its
 * Refund amount, its status, once approved its Send-back deadline and Warehouse, its Inspection and
 * resolution once it has them, and its RMA history, oldest first. A null field is left out.
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
    MoneyResponse refundAmount,
    String status,
    String requestedAt,
    String sendBackBy,
    String warehouseId,
    InspectionResponse inspection,
    ResolutionResponse resolution,
    List<HistoryResponse> history) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record WindowResponse(String kind, String startsAt, String endsAt, WarrantyResponse warranty) {}

  record WarrantyResponse(int months, String manufacturer) {}

  record MoneyResponse(long amountMinor, String currency) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record InspectionResponse(String outcome, String note) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record ResolutionResponse(String paymentTransactionReference, String replacementShipmentId) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record HistoryResponse(String status, String by, String at, String note) {}

  static RmaResponse of(Rma rma) {
    var window = rma.window();
    var warranty = window.warranty();
    var refund = rma.refundAmount();
    var inspection = rma.inspection();
    var resolution = rma.resolution();
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
        refund == null ? null : new MoneyResponse(refund.amountMinor(), refund.currency()),
        rma.status().name(),
        rma.requestedAt().toString(),
        text(rma.sendBackBy()),
        rma.warehouseId(),
        inspection == null
            ? null
            : new InspectionResponse(inspection.outcome().name(), inspection.note()),
        resolution == null
            ? null
            : new ResolutionResponse(
                resolution.paymentTransactionReference(),
                resolution.replacementShipmentId() == null
                    ? null
                    : resolution.replacementShipmentId().toString()),
        rma.history().stream()
            .map(e -> new HistoryResponse(e.status().name(), e.by(), e.at().toString(), e.note()))
            .toList());
  }

  private static String text(Instant instant) {
    return instant == null ? null : instant.toString();
  }
}
