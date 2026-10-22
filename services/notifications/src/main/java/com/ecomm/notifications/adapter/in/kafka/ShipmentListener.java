package com.ecomm.notifications.adapter.in.kafka;

import com.ecomm.notifications.application.port.in.ShipmentEventsUseCase;
import com.ecomm.notifications.application.port.in.ShipmentEventsUseCase.ShipmentChanged;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Fulfillment & Shipping's Shipment events, which tell Customers their parcels' progress. */
@Component
class ShipmentListener {

  private static final Logger log = LoggerFactory.getLogger(ShipmentListener.class);

  /**
   * The fields of a {@code fulfillment-shipping.shipment} event Notifications needs. A missing
   * {@code kind} is an {@code ORDER} Shipment, published before replacements existed.
   */
  record ShipmentEvent(
      @JsonProperty(required = true) String eventId,
      @JsonProperty(required = true) Instant occurredAt,
      @JsonProperty(required = true) String change,
      @JsonProperty(required = true) Shipment shipment) {
    record Shipment(
        String kind,
        String rmaId,
        @JsonProperty(required = true) String orderId,
        @JsonProperty(required = true) String customerId,
        @JsonProperty(required = true) List<Line> lines,
        String carrier,
        String trackingNumber,
        Instant deliveredAt) {}

    record Line(
        @JsonProperty(required = true) String productName,
        @JsonProperty(required = true) int quantity) {}
  }

  private final ShipmentEventsUseCase shipments;

  ShipmentListener(ShipmentEventsUseCase shipments) {
    this.shipments = shipments;
  }

  /**
   * On its first start the group reads from the latest offset, so no Shipment from before
   * Notifications existed is announced; from then on it resumes where it left off.
   */
  @KafkaListener(
      topics = "fulfillment-shipping.shipment",
      groupId = "notifications.shipments",
      properties = "auto.offset.reset=latest")
  void on(ShipmentEvent event) {
    var shipment = event.shipment();
    var recorded =
        shipments.shipmentChanged(
            new ShipmentChanged(
                event.eventId(),
                event.occurredAt(),
                event.change(),
                shipment.kind() == null ? "ORDER" : shipment.kind(),
                shipment.orderId(),
                shipment.rmaId(),
                shipment.customerId(),
                shipment.carrier(),
                shipment.trackingNumber(),
                shipment.deliveredAt(),
                shipment.lines().stream()
                    .map(l -> new ShipmentChanged.Line(l.productName(), l.quantity()))
                    .toList()));
    if (!recorded.isEmpty()) {
      log.info(
          "Recorded {} for Order {} from event {}", recorded, shipment.orderId(), event.eventId());
    }
  }
}
