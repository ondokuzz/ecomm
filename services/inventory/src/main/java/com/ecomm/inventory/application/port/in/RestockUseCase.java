package com.ecomm.inventory.application.port.in;

import com.ecomm.inventory.domain.Stock;

public interface RestockUseCase {

  /**
   * Puts {@code quantity} returned units back on hand as a {@code RESTOCKED} movement naming the
   * reference, and returns the Variant's Stock after it. A Variant restocked under that reference
   * already is restocked no more, and its Stock is returned as it is: a returns Saga run again,
   * with keys of its own, restocks an RMA's units once. Throws {@code UnknownVariantException} when
   * Inventory doesn't stock the Variant, and {@code InvalidStockRequestException} for a quantity
   * below 1 or a missing or overlong reference.
   */
  Stock restock(String variantId, int quantity, String reference);
}
