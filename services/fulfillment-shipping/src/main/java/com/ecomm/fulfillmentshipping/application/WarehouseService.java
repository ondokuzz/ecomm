package com.ecomm.fulfillmentshipping.application;

import com.ecomm.fulfillmentshipping.application.port.in.ReadWarehousesUseCase;
import com.ecomm.fulfillmentshipping.domain.Warehouse;
import com.ecomm.fulfillmentshipping.domain.Warehouses;
import java.util.List;
import java.util.Optional;

public class WarehouseService implements ReadWarehousesUseCase {

  private final Warehouses warehouses;

  public WarehouseService(Warehouses warehouses) {
    this.warehouses = warehouses;
  }

  @Override
  public List<Warehouse> list() {
    return warehouses.all();
  }

  @Override
  public Optional<Warehouse> warehouse(String id) {
    return warehouses.find(id);
  }
}
