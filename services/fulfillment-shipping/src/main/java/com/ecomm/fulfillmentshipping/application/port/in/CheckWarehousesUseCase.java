package com.ecomm.fulfillmentshipping.application.port.in;

public interface CheckWarehousesUseCase {

  /**
   * @throws IllegalStateException listing the Shipments, when a Shipment names a Warehouse the
   *     configuration no longer has
   */
  void checkEveryShipmentsWarehouseIsConfigured();
}
