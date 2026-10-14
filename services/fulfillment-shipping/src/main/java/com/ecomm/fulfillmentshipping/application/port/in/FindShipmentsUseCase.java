package com.ecomm.fulfillmentshipping.application.port.in;

import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShipmentStatus;
import java.util.Optional;
import java.util.UUID;

public interface FindShipmentsUseCase {

  Optional<Shipment> shipment(UUID id);

  /** The Order's Shipment, if it has one. */
  Optional<Shipment> shipmentOf(String orderId);

  /**
   * A page of Shipments, oldest first, in {@code status} and from {@code warehouseId} when they are
   * given.
   */
  Page<Shipment> shipments(ShipmentStatus status, String warehouseId, int page, int size);
}
