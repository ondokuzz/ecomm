package com.ecomm.fulfillmentshipping.application.port.out;

import com.ecomm.commons.events.IntegrationEvent;
import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShipmentStatus;
import java.util.List;

/**
 * The {@code fulfillment-shipping.shipment} event: a snapshot of a Shipment, published with each
 * change. Its shape is defined by {@code
 * platform/event-schemas/schemas/fulfillment-shipping.shipment.json}.
 */
public record ShipmentEvent(String shipmentId, long version, Change change, Snapshot shipment)
    implements IntegrationEvent {

  public static final String TOPIC = "fulfillment-shipping.shipment";

  /** Why the event was published: the status moved to, or a Tracking event recorded. */
  public enum Change {
    CREATED,
    PICKED,
    PACKED,
    SHIPPED,
    DELIVERED,
    ON_HOLD,
    TRACKED;

    /** The change that moved a Shipment to {@code status}. */
    public static Change movedTo(ShipmentStatus status) {
      return switch (status) {
        case AWAITING_PICK -> CREATED;
        case PICKED -> PICKED;
        case PACKED -> PACKED;
        case SHIPPED -> SHIPPED;
        case DELIVERED -> DELIVERED;
        case ON_HOLD -> ON_HOLD;
      };
    }
  }

  /** The null fields are left out. */
  public record Snapshot(
      String orderId,
      String customerId,
      String warehouseId,
      String status,
      List<Line> lines,
      String carrier,
      String trackingNumber,
      List<Tracking> trackingEvents,
      String deliveredAt,
      String holdReason,
      List<History> statusHistory,
      String createdAt) {}

  public record Line(
      String variantId,
      String productName,
      int quantity,
      String serialTracking,
      List<String> serialNumbers) {}

  public record Tracking(String eventId, String status, String occurredAt) {}

  public record History(String status, String at, String by) {}

  public static ShipmentEvent of(Shipment shipment, Change change) {
    return new ShipmentEvent(
        shipment.id().toString(),
        shipment.version(),
        change,
        new Snapshot(
            shipment.orderId(),
            shipment.customerId(),
            shipment.warehouseId(),
            shipment.status().name(),
            shipment.lines().stream()
                .map(
                    l ->
                        new Line(
                            l.variantId(),
                            l.productName(),
                            l.quantity(),
                            l.serialTracking().name(),
                            l.serialNumbers()))
                .toList(),
            shipment.carrier(),
            shipment.trackingNumber(),
            shipment.trackingEvents().stream()
                .map(e -> new Tracking(e.eventId(), e.status().name(), e.occurredAt().toString()))
                .toList(),
            shipment.deliveredAt() == null ? null : shipment.deliveredAt().toString(),
            shipment.holdReason(),
            shipment.statusHistory().stream()
                .map(h -> new History(h.status().name(), h.at().toString(), h.by()))
                .toList(),
            shipment.createdAt().toString()));
  }

  @Override
  public String topic() {
    return TOPIC;
  }

  @Override
  public String aggregateId() {
    return shipmentId;
  }
}
