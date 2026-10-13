package com.ecomm.fulfillmentshipping.application;

import com.ecomm.fulfillmentshipping.application.port.in.ListWarehousesUseCase;
import com.ecomm.fulfillmentshipping.domain.Warehouse;
import com.ecomm.fulfillmentshipping.domain.Warehouses;
import java.util.List;

public class WarehouseService implements ListWarehousesUseCase {

  private final Warehouses warehouses;

  public WarehouseService(Warehouses warehouses) {
    this.warehouses = warehouses;
  }

  @Override
  public List<Warehouse> list() {
    return warehouses.all();
  }
}
