package com.ecomm.fulfillmentshipping.domain;

import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.IllegalTransitionException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.InvalidSerialNumbersException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One Order's parcel, from its Warehouse to its Shipping address. Each change gives a new Shipment,
 * one version on, with an entry appended to its status history when its status moves (see {@link
 * ShipmentStatus}).
 *
 * @param warehouseId the Warehouse it ships from
 * @param carrier who carries it, once booked; null before
 * @param trackingNumber the Carrier's, once booked; null before
 * @param trackingEvents every Tracking event the Carrier reported, oldest first
 * @param deliveredAt when the Carrier delivered it; null until then
 * @param holdReason why it is on hold, such as the capture's decline reason; null unless it is
 * @param version 1 when created, and one more with each change
 */
public record Shipment(
    UUID id,
    String orderId,
    String customerId,
    String warehouseId,
    ShippingAddress shippingAddress,
    ShippingOption shippingOption,
    List<ShipmentLine> lines,
    ShipmentStatus status,
    List<StatusChange> statusHistory,
    String carrier,
    String trackingNumber,
    List<TrackingEvent> trackingEvents,
    Instant deliveredAt,
    String holdReason,
    Instant createdAt,
    long version) {

  public Shipment {
    lines = List.copyOf(lines);
    statusHistory = List.copyOf(statusHistory);
    trackingEvents = trackingEvents.stream().sorted(TrackingEvent.IN_TIME_ORDER).toList();
  }

  /** A new Shipment awaiting pick, created by the fulfillment Saga. */
  public static Shipment create(
      UUID id,
      String orderId,
      String customerId,
      Warehouse warehouse,
      ShippingAddress address,
      ShippingOption option,
      List<ShipmentLine> lines,
      Instant at) {
    return new Shipment(
        id,
        orderId,
        customerId,
        warehouse.id(),
        address,
        option,
        lines,
        ShipmentStatus.AWAITING_PICK,
        List.of(new StatusChange(ShipmentStatus.AWAITING_PICK, at, StatusChange.ORCHESTRATION)),
        null,
        null,
        List.of(),
        null,
        null,
        at,
        1);
  }

  public boolean belongsTo(String customerId) {
    return this.customerId.equals(customerId);
  }

  /** Staff have picked it from the shelves. */
  public Shipment picked(String staffId, Instant at) {
    return movedTo(ShipmentStatus.PICKED, at, staffId);
  }

  /**
   * Staff have packed it, entering one Serial number per unit of each serial-tracked line, keyed by
   * Variant ID. Each is checked against its line's Serial tracking and kept as normalised.
   *
   * @throws InvalidSerialNumbersException naming each malformed one, each line given the wrong
   *     count, each Serial number entered twice, and each Variant entered that isn't serial-tracked
   *     or isn't in the Shipment
   */
  public Shipment packed(Map<String, List<String>> entered, String staffId, Instant at) {
    var moved = movedTo(ShipmentStatus.PACKED, at, staffId);
    var violations = new ArrayList<FieldViolation>();
    var seen = new HashSet<String>();
    var packedLines = new ArrayList<ShipmentLine>();
    for (var line : lines) {
      var field = "serialNumbers." + line.variantId();
      var given = entered.getOrDefault(line.variantId(), List.of());
      if (!line.serialTracking().tracked()) {
        if (!given.isEmpty()) {
          violations.add(new FieldViolation(field, "must be left out: the Variant isn't tracked"));
        }
        packedLines.add(line);
        continue;
      }
      if (given.size() != line.quantity()) {
        violations.add(
            new FieldViolation(
                field,
                "needs "
                    + line.quantity()
                    + " Serial numbers, one per unit, but has "
                    + given.size()));
      }
      var kept = new ArrayList<String>();
      for (var i = 0; i < given.size(); i++) {
        var unitField = field + "[" + i + "]";
        var serial = line.serialTracking().normalise(given.get(i));
        if (serial.isEmpty()) {
          violations.add(new FieldViolation(unitField, line.serialTracking().form()));
        } else if (!seen.add(serial.get())) {
          violations.add(
              new FieldViolation(unitField, "is entered twice in this Shipment: " + serial.get()));
        } else {
          kept.add(serial.get());
        }
      }
      packedLines.add(line.withSerialNumbers(kept));
    }
    for (var variantId : entered.keySet()) {
      if (lines.stream().noneMatch(line -> line.variantId().equals(variantId))) {
        violations.add(
            new FieldViolation(
                "serialNumbers." + variantId, "must be left out: the Variant isn't shipped here"));
      }
    }
    if (!violations.isEmpty()) {
      throw new InvalidSerialNumbersException(violations);
    }
    return moved.with(packedLines);
  }

  /** Every Serial number packed in it. */
  public List<String> serialNumbers() {
    return lines.stream().flatMap(line -> line.serialNumbers().stream()).toList();
  }

  /** The fulfillment Saga has handed it to the Carrier, which booked it. */
  public Shipment shipped(Booking booking, Instant at) {
    var moved = movedTo(ShipmentStatus.SHIPPED, at, StatusChange.ORCHESTRATION);
    return new Shipment(
        id,
        orderId,
        customerId,
        warehouseId,
        shippingAddress,
        shippingOption,
        lines,
        moved.status,
        moved.statusHistory,
        booking.carrier(),
        booking.trackingNumber(),
        trackingEvents,
        deliveredAt,
        holdReason,
        createdAt,
        moved.version);
  }

  /** The fulfillment Saga holds it, as its Payment couldn't be captured, for {@code reason}. */
  public Shipment held(String reason, Instant at) {
    var moved = movedTo(ShipmentStatus.ON_HOLD, at, StatusChange.ORCHESTRATION);
    return new Shipment(
        id,
        orderId,
        customerId,
        warehouseId,
        shippingAddress,
        shippingOption,
        lines,
        moved.status,
        moved.statusHistory,
        carrier,
        trackingNumber,
        trackingEvents,
        deliveredAt,
        reason,
        createdAt,
        moved.version);
  }

  /**
   * The Carrier reported {@code event}, which is kept in time order whenever it arrives. Only a
   * delivery moves the Shipment on, from {@code SHIPPED} to {@code DELIVERED} at the event's time,
   * so the status never goes back however late an earlier event arrives.
   */
  public Shipment tracked(TrackingEvent event) {
    var events = new ArrayList<>(trackingEvents);
    events.add(event);
    var delivers =
        event.status() == TrackingStatus.DELIVERED && status.canMoveTo(ShipmentStatus.DELIVERED);
    var history = new ArrayList<>(statusHistory);
    if (delivers) {
      history.add(
          new StatusChange(ShipmentStatus.DELIVERED, event.occurredAt(), StatusChange.CARRIER));
    }
    return new Shipment(
        id,
        orderId,
        customerId,
        warehouseId,
        shippingAddress,
        shippingOption,
        lines,
        delivers ? ShipmentStatus.DELIVERED : status,
        history,
        carrier,
        trackingNumber,
        events,
        delivers ? event.occurredAt() : deliveredAt,
        holdReason,
        createdAt,
        version + 1);
  }

  private Shipment movedTo(ShipmentStatus next, Instant at, String by) {
    if (!status.canMoveTo(next)) {
      throw new IllegalTransitionException(id, status, next);
    }
    var history = new ArrayList<>(statusHistory);
    history.add(new StatusChange(next, at, by));
    return new Shipment(
        id,
        orderId,
        customerId,
        warehouseId,
        shippingAddress,
        shippingOption,
        lines,
        next,
        history,
        carrier,
        trackingNumber,
        trackingEvents,
        deliveredAt,
        holdReason,
        createdAt,
        version + 1);
  }

  private Shipment with(List<ShipmentLine> newLines) {
    return new Shipment(
        id,
        orderId,
        customerId,
        warehouseId,
        shippingAddress,
        shippingOption,
        newLines,
        status,
        statusHistory,
        carrier,
        trackingNumber,
        trackingEvents,
        deliveredAt,
        holdReason,
        createdAt,
        version);
  }
}
