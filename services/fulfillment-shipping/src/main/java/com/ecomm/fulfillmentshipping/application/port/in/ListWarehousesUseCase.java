package com.ecomm.fulfillmentshipping.application.port.in;

import com.ecomm.fulfillmentshipping.domain.Warehouse;
import java.util.List;

public interface ListWarehousesUseCase {

  /** Every configured Warehouse, in configuration order. */
  List<Warehouse> list();
}
