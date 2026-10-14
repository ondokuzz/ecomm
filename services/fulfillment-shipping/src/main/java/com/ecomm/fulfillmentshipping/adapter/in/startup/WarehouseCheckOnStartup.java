package com.ecomm.fulfillmentshipping.adapter.in.startup;

import com.ecomm.fulfillmentshipping.application.port.in.CheckWarehousesUseCase;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Refuses to start, listing the Shipments, when a Shipment names a Warehouse the configuration no
 * longer has: such a Shipment would never show in any Warehouse's queue. It runs once every bean,
 * Flyway's migration among them, is ready, and before the service takes requests.
 */
@Component
class WarehouseCheckOnStartup implements SmartInitializingSingleton {

  private final CheckWarehousesUseCase check;

  WarehouseCheckOnStartup(CheckWarehousesUseCase check) {
    this.check = check;
  }

  @Override
  public void afterSingletonsInstantiated() {
    check.checkEveryShipmentsWarehouseIsConfigured();
  }
}
