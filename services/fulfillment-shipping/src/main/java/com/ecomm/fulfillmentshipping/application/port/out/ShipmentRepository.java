package com.ecomm.fulfillmentshipping.application.port.out;

import com.ecomm.fulfillmentshipping.application.port.in.Page;
import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.SerialNumberAlreadyShippedException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.ShipmentExistsException;
import com.ecomm.fulfillmentshipping.domain.ShipmentStatus;
import com.ecomm.fulfillmentshipping.domain.TrackingEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where Shipments are kept. Each change is written in the caller's transaction. */
public interface ShipmentRepository {

  /**
   * @throws ShipmentExistsException when the Order already has a Shipment
   */
  void add(Shipment shipment);

  /**
   * Writes {@code changed}, a later version of a Shipment read with a lock: its status, booking and
   * delivery, its new status history entries, and the Serial numbers it was packed with.
   *
   * @throws SerialNumberAlreadyShippedException when another Shipment carries one of them
   */
  void update(Shipment changed);

  /**
   * Keeps {@code event} as received for the Shipment, unless one with its ID already is.
   *
   * @return whether it was new
   */
  boolean addTrackingEvent(UUID shipmentId, TrackingEvent event, Instant receivedAt);

  Optional<Shipment> find(UUID id);

  Optional<Shipment> findByOrder(String orderId);

  /** The Shipment, locked until the transaction ends so that changes to it queue. */
  Optional<Shipment> lockToChange(UUID id);

  /** The Shipment the Carrier knows by {@code trackingNumber}, locked as {@link #lockToChange}. */
  Optional<Shipment> lockToChangeByTrackingNumber(String trackingNumber);

  /**
   * Which of {@code shipment}'s Serial numbers another Shipment, not on hold, carries for the same
   * Product.
   */
  List<String> shippedElsewhere(Shipment shipment);

  /**
   * A page of Shipments, oldest first, in {@code status} and from {@code warehouseId} when they are
   * given.
   */
  Page<Shipment> page(ShipmentStatus status, String warehouseId, int page, int size);

  /** The IDs and Warehouses of the Shipments from any Warehouse but {@code warehouseIds}. */
  List<String> fromOtherWarehouses(Collection<String> warehouseIds);
}
