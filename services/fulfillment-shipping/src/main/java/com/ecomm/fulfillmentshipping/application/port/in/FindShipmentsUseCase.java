package com.ecomm.fulfillmentshipping.application.port.in;

import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShipmentKind;
import com.ecomm.fulfillmentshipping.domain.ShipmentStatus;
import java.util.Optional;
import java.util.UUID;

public interface FindShipmentsUseCase {

  Optional<Shipment> shipment(UUID id);

  /** The Order's {@code ORDER} Shipment, if it has one. */
  Optional<Shipment> shipmentOf(String orderId);

  /** The RMA's replacement Shipment, if it has one. */
  Optional<Shipment> replacementOf(String rmaId);

  /**
   * A page of Shipments, oldest first, in {@code status}, from {@code warehouseId} and of {@code
   * kind} when they are given.
   */
  Page<Shipment> shipments(
      ShipmentStatus status, String warehouseId, ShipmentKind kind, int page, int size);
}
