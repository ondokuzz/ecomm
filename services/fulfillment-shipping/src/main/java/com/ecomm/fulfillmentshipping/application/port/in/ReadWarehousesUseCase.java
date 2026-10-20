package com.ecomm.fulfillmentshipping.application.port.in;

import com.ecomm.fulfillmentshipping.domain.Warehouse;
import java.util.List;
import java.util.Optional;

public interface ReadWarehousesUseCase {

  /** Every configured Warehouse, in configuration order. */
  List<Warehouse> list();

  /** The configured Warehouse {@code id}, if there is one. */
  Optional<Warehouse> warehouse(String id);
}
