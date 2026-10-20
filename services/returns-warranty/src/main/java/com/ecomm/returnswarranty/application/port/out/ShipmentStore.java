package com.ecomm.returnswarranty.application.port.out;

import com.ecomm.returnswarranty.domain.Delivery;
import com.ecomm.returnswarranty.domain.Shipment;
import java.util.Optional;
import java.util.UUID;

/** The Shipment projection. */
public interface ShipmentStore {

  /**
   * The version of the Shipment held for {@code id}, if any. One kept before Warehouses were has
   * none, so that it is applied again.
   */
  Optional<Long> versionOf(UUID id);

  /** Keeps the Shipment, replacing what was held for it. */
  void save(Shipment shipment);

  /**
   * The Order's Shipment, if it is delivered, locked until the transaction ends so that RMAs for
   * the Order are checked one at a time.
   */
  Optional<Delivery> lockDeliveryOf(String orderId);

  /** The Order's Shipment, if it is delivered. */
  Optional<Delivery> deliveryOf(String orderId);

  /** Whether any Shipment was kept before Warehouses were, and so has none. */
  boolean anyWithoutWarehouse();
}
