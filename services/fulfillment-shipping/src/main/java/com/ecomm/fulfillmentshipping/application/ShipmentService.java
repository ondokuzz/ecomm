package com.ecomm.fulfillmentshipping.application;

import com.ecomm.commons.events.IntegrationEventPublisher;
import com.ecomm.fulfillmentshipping.application.port.in.ChangeShipmentUseCase;
import com.ecomm.fulfillmentshipping.application.port.in.CheckWarehousesUseCase;
import com.ecomm.fulfillmentshipping.application.port.in.CreateShipmentUseCase;
import com.ecomm.fulfillmentshipping.application.port.in.FindShipmentsUseCase;
import com.ecomm.fulfillmentshipping.application.port.in.Page;
import com.ecomm.fulfillmentshipping.application.port.in.RecordTrackingEventUseCase;
import com.ecomm.fulfillmentshipping.application.port.out.ProductStore;
import com.ecomm.fulfillmentshipping.application.port.out.ShipmentEvent;
import com.ecomm.fulfillmentshipping.application.port.out.ShipmentRepository;
import com.ecomm.fulfillmentshipping.application.port.out.ShippingProvider;
import com.ecomm.fulfillmentshipping.application.port.out.TimeSource;
import com.ecomm.fulfillmentshipping.application.port.out.Transactions;
import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.IllegalTransitionException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.ProductsNotKnownYetException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.SerialNumberAlreadyShippedException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.ShipmentExistsException;
import com.ecomm.fulfillmentshipping.domain.ShipmentKind;
import com.ecomm.fulfillmentshipping.domain.ShipmentLine;
import com.ecomm.fulfillmentshipping.domain.ShipmentStatus;
import com.ecomm.fulfillmentshipping.domain.TrackingEvent;
import com.ecomm.fulfillmentshipping.domain.Warehouse;
import com.ecomm.fulfillmentshipping.domain.Warehouses;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * A Shipment's life, from creation to delivery. Each change is made under the Shipment's lock, with
 * its status history entry and its {@code fulfillment-shipping.shipment} event, in one transaction.
 */
public class ShipmentService
    implements CreateShipmentUseCase,
        ChangeShipmentUseCase,
        FindShipmentsUseCase,
        RecordTrackingEventUseCase,
        CheckWarehousesUseCase {

  /** How many Shipments a refusal to start lists, at most. */
  private static final int LISTED = 20;

  private final ShipmentRepository shipments;
  private final ProductStore products;
  private final Warehouses warehouses;
  private final ShippingProvider carrier;
  private final IntegrationEventPublisher events;
  private final Transactions transactions;
  private final TimeSource time;

  public ShipmentService(
      ShipmentRepository shipments,
      ProductStore products,
      Warehouses warehouses,
      ShippingProvider carrier,
      IntegrationEventPublisher events,
      Transactions transactions,
      TimeSource time) {
    this.shipments = shipments;
    this.products = products;
    this.warehouses = warehouses;
    this.carrier = carrier;
    this.events = events;
    this.transactions = transactions;
    this.time = time;
  }

  @Override
  public Shipment create(NewShipment newShipment) {
    var variantIds = newShipment.lines().stream().map(l -> l.variantId()).toList();
    var known = products.productsOf(variantIds);
    var unknown = variantIds.stream().filter(id -> !known.containsKey(id)).toList();
    if (!unknown.isEmpty()) {
      throw new ProductsNotKnownYetException(unknown);
    }
    var lines =
        newShipment.lines().stream()
            .map(
                line -> {
                  var product = known.get(line.variantId());
                  return new ShipmentLine(
                      line.variantId(),
                      product.name(),
                      line.quantity(),
                      product.serialTracking(),
                      List.of());
                })
            .toList();
    Warehouse warehouse = warehouses.assignedTo(newShipment.shippingAddress().country());
    return transactions.inTransaction(
        () -> {
          var replacement = newShipment.kind() == ShipmentKind.REPLACEMENT;
          var existing =
              replacement
                  ? shipments.findByRma(newShipment.rmaId())
                  : shipments.findByOrder(newShipment.orderId());
          if (existing.isPresent()) {
            throw new ShipmentExistsException(
                newShipment.kind(), newShipment.orderId(), newShipment.rmaId());
          }
          var shipment =
              Shipment.create(
                  UUID.randomUUID(),
                  newShipment.kind(),
                  newShipment.rmaId(),
                  newShipment.orderId(),
                  newShipment.customerId(),
                  warehouse,
                  newShipment.shippingAddress(),
                  newShipment.shippingOption(),
                  lines,
                  time.now());
          shipments.add(shipment);
          events.publish(ShipmentEvent.of(shipment, ShipmentEvent.Change.CREATED));
          return shipment;
        });
  }

  @Override
  public Optional<Shipment> pick(UUID id, String staffId) {
    return change(id, shipment -> shipment.picked(staffId, time.now()));
  }

  @Override
  public Optional<Shipment> pack(UUID id, Map<String, List<String>> serialNumbers, String staffId) {
    return change(
        id,
        shipment -> {
          var packed = shipment.packed(serialNumbers, staffId, time.now());
          var elsewhere = shipments.shippedElsewhere(packed);
          if (!elsewhere.isEmpty()) {
            throw new SerialNumberAlreadyShippedException(elsewhere.getFirst());
          }
          return packed;
        });
  }

  /**
   * Books with the Carrier inside the transaction, so a refused move books nothing; the Carrier
   * answers a retried key with its first booking.
   */
  @Override
  public Optional<Shipment> dispatch(UUID id, String idempotencyKey) {
    return change(
        id,
        shipment -> {
          if (!shipment.status().canMoveTo(ShipmentStatus.SHIPPED)) {
            throw new IllegalTransitionException(
                shipment.id(), shipment.status(), ShipmentStatus.SHIPPED);
          }
          return shipment.shipped(carrier.book(shipment, idempotencyKey), time.now());
        });
  }

  @Override
  public Optional<Shipment> hold(UUID id, String reason) {
    return change(id, shipment -> shipment.held(reason, time.now()));
  }

  private Optional<Shipment> change(UUID id, UnaryOperator<Shipment> move) {
    return transactions.inTransaction(
        () ->
            shipments
                .lockToChange(id)
                .map(
                    shipment -> {
                      var changed = move.apply(shipment);
                      shipments.update(changed);
                      events.publish(
                          ShipmentEvent.of(
                              changed, ShipmentEvent.Change.movedTo(changed.status())));
                      return changed;
                    }));
  }

  @Override
  public Optional<Recorded> record(String trackingNumber, TrackingEvent event) {
    return transactions.inTransaction(
        () ->
            shipments
                .lockToChangeByTrackingNumber(trackingNumber)
                .map(
                    shipment -> {
                      if (!shipments.addTrackingEvent(shipment.id(), event, time.now())) {
                        return new Recorded(shipment, false);
                      }
                      var tracked = shipment.tracked(event);
                      shipments.update(tracked);
                      var change =
                          tracked.status() == shipment.status()
                              ? ShipmentEvent.Change.TRACKED
                              : ShipmentEvent.Change.movedTo(tracked.status());
                      events.publish(ShipmentEvent.of(tracked, change));
                      return new Recorded(tracked, true);
                    }));
  }

  @Override
  public Optional<Shipment> shipment(UUID id) {
    return shipments.find(id);
  }

  @Override
  public Optional<Shipment> shipmentOf(String orderId) {
    return shipments.findByOrder(orderId);
  }

  @Override
  public Optional<Shipment> replacementOf(String rmaId) {
    return shipments.findByRma(rmaId);
  }

  @Override
  public Page<Shipment> shipments(
      ShipmentStatus status, String warehouseId, ShipmentKind kind, int page, int size) {
    return shipments.page(status, warehouseId, kind, page, size);
  }

  @Override
  public void checkEveryShipmentsWarehouseIsConfigured() {
    var configured = warehouses.all().stream().map(Warehouse::id).toList();
    var stranded = shipments.fromOtherWarehouses(configured);
    if (!stranded.isEmpty()) {
      throw new IllegalStateException(
          stranded.size()
              + " Shipments name a Warehouse that isn't configured (configured: "
              + configured
              + "): "
              + String.join(", ", stranded.subList(0, Math.min(LISTED, stranded.size())))
              + (stranded.size() > LISTED ? ", ..." : ""));
    }
  }
}
