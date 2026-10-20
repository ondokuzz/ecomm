package com.ecomm.returnswarranty.application.port.out;

import com.ecomm.commons.events.IntegrationEvent;
import com.ecomm.returnswarranty.domain.Rma;
import java.time.Instant;
import java.util.List;

/**
 * The {@code returns-warranty.rma} event: a snapshot of an RMA, published with each change. Its
 * shape is defined by {@code platform/event-schemas/schemas/returns-warranty.rma.json}.
 */
public record RmaEvent(String rmaId, long version, String change, Snapshot rma)
    implements IntegrationEvent {

  public static final String TOPIC = "returns-warranty.rma";

  /**
   * The null fields are left out. {@code decisions} is the RMA history: the schema keeps the name
   * it had when it held only RMA decisions, since a field can't be renamed compatibly.
   */
  public record Snapshot(
      String reference,
      String orderId,
      String customerId,
      String variantId,
      String productName,
      int quantity,
      List<String> serialNumbers,
      String kind,
      String reason,
      Window window,
      String status,
      String requestedAt,
      Money refundAmount,
      String sendBackBy,
      String warehouseId,
      Inspection inspection,
      Resolution resolution,
      List<Decision> decisions) {}

  public record Window(String kind, String startsAt, String endsAt, Warranty warranty) {}

  public record Warranty(int months, String manufacturer) {}

  public record Money(long amountMinor, String currency) {}

  public record Inspection(String outcome, String note) {}

  public record Resolution(String paymentTransactionReference, String replacementShipmentId) {}

  public record Decision(String status, String decidedBy, String decidedAt, String note) {}

  /** Why an RMA that took no step is published: what it lacked from before Sprint 6 filled in. */
  public static final String BACKFILLED = "BACKFILLED";

  /** The RMA as it is now: {@code REQUESTED} when new, else the step that moved it. */
  public static RmaEvent of(Rma rma) {
    return of(rma, rma.status().name());
  }

  /** The RMA as it is now, its Warehouse or Refund amount just filled in. */
  public static RmaEvent backfilled(Rma rma) {
    return of(rma, BACKFILLED);
  }

  private static RmaEvent of(Rma rma, String change) {
    var window = rma.window();
    var warranty = window.warranty();
    var refund = rma.refundAmount();
    var inspection = rma.inspection();
    var resolution = rma.resolution();
    return new RmaEvent(
        rma.id().toString(),
        rma.version(),
        change,
        new Snapshot(
            rma.reference(),
            rma.orderId(),
            rma.customerId(),
            rma.variantId(),
            rma.productName(),
            rma.quantity(),
            rma.serialNumbers(),
            rma.kind().name(),
            rma.reason(),
            new Window(
                window.kind().name(),
                window.startsAt().toString(),
                window.endsAt().toString(),
                warranty == null ? null : new Warranty(warranty.months(), warranty.manufacturer())),
            rma.status().name(),
            rma.requestedAt().toString(),
            refund == null ? null : new Money(refund.amountMinor(), refund.currency()),
            text(rma.sendBackBy()),
            rma.warehouseId(),
            inspection == null
                ? null
                : new Inspection(inspection.outcome().name(), inspection.note()),
            resolution == null
                ? null
                : new Resolution(
                    resolution.paymentTransactionReference(),
                    resolution.replacementShipmentId() == null
                        ? null
                        : resolution.replacementShipmentId().toString()),
            rma.history().stream()
                .map(e -> new Decision(e.status().name(), e.by(), e.at().toString(), e.note()))
                .toList()));
  }

  private static String text(Instant instant) {
    return instant == null ? null : instant.toString();
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public String aggregateId() {
    return rmaId;
  }
}
