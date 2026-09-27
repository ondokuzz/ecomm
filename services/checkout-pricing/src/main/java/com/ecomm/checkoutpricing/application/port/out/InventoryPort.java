package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.CartLine;
import com.ecomm.checkoutpricing.domain.OutOfStockException;
import java.util.List;

/** Inventory's Stock. */
public interface InventoryPort {

  /**
   * Takes the lines' quantities off Stock, all or none.
   *
   * @throws OutOfStockException when some Variant doesn't have enough
   */
  void decrement(List<CartLine> lines);
}
