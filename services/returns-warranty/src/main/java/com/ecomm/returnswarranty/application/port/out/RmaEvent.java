package com.ecomm.returnswarranty.application.port.out;

import com.ecomm.commons.events.IntegrationEvent;
import com.ecomm.returnswarranty.domain.Rma;
import java.util.List;

/**
 * The {@code returns-warranty.rma} event: a snapshot of an RMA, published with each change. Its
 * shape is defined by {@code platform/event-schemas/schemas/returns-warranty.rma.json}.
 */
public record RmaEvent(String rmaId, long version, String change, Snapshot rma)
    implements IntegrationEvent {

  public static final String TOPIC = "returns-warranty.rma";

  /** The null fields are left out. */
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
      List<Decision> decisions) {}

  public record Window(String kind, String startsAt, String endsAt, Warranty warranty) {}

  public record Warranty(int months, String manufacturer) {}

  public record Decision(String status, String decidedBy, String decidedAt, String note) {}

  /** The RMA as it is now: {@code REQUESTED} when new, else the decision that moved it. */
  public static RmaEvent of(Rma rma) {
    var window = rma.window();
    var warranty = window.warranty();
    return new RmaEvent(
        rma.id().toString(),
        rma.version(),
        rma.status().name(),
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
            rma.decisions().stream()
                .map(
                    d ->
                        new Decision(
                            d.status().name(), d.decidedBy(), d.decidedAt().toString(), d.note()))
                .toList()));
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
