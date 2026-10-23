package com.ecomm.fulfillmentshipping.adapter.in.web;

import com.ecomm.fulfillmentshipping.application.port.in.ReadWarehousesUseCase;
import com.ecomm.fulfillmentshipping.domain.Warehouse;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff read the configured Warehouses. Any signed-in Customer reads one's name and return address,
 * to send an RMA's units back; Staff, Orchestration and Notifications, for the RMA's approval
 * email, read it too.
 */
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

  record ReturnAddressResponse(String id, String name, Warehouse.ReturnAddress returnAddress) {

    static ReturnAddressResponse of(Warehouse warehouse) {
      return new ReturnAddressResponse(warehouse.id(), warehouse.name(), warehouse.returnAddress());
    }
  }

  private final ReadWarehousesUseCase warehouses;

  WarehouseController(ReadWarehousesUseCase warehouses) {
    this.warehouses = warehouses;
  }

  @GetMapping("/warehouses")
  @PreAuthorize("hasRole('STAFF')")
  List<WarehouseResponse> list() {
    return warehouses.list().stream().map(WarehouseResponse::of).toList();
  }

  /** 404 for a Warehouse that isn't configured. */
  @GetMapping("/warehouses/{id}")
  @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'ORCHESTRATION', 'NOTIFICATIONS')")
  ResponseEntity<ReturnAddressResponse> warehouse(@PathVariable String id) {
    return ResponseEntity.of(warehouses.warehouse(id).map(ReturnAddressResponse::of));
  }
}
