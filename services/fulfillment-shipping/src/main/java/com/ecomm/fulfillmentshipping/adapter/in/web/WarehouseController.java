package com.ecomm.fulfillmentshipping.adapter.in.web;

import com.ecomm.fulfillmentshipping.application.port.in.ListWarehousesUseCase;
import com.ecomm.fulfillmentshipping.domain.Warehouse;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff read the configured Warehouses. */
@RestController
class WarehouseController {

  record WarehouseResponse(
      String id,
      String name,
      Warehouse.Address address,
      @JsonProperty("default") boolean isDefault) {

    static WarehouseResponse of(Warehouse warehouse) {
      return new WarehouseResponse(
          warehouse.id(), warehouse.name(), warehouse.address(), warehouse.isDefault());
    }
  }

  private final ListWarehousesUseCase warehouses;

  WarehouseController(ListWarehousesUseCase warehouses) {
    this.warehouses = warehouses;
  }

  @GetMapping("/warehouses")
  @PreAuthorize("hasRole('STAFF')")
  List<WarehouseResponse> list() {
    return warehouses.list().stream().map(WarehouseResponse::of).toList();
  }
}
