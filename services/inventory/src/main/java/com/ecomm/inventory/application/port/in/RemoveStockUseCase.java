package com.ecomm.inventory.application.port.in;

public interface RemoveStockUseCase {

  /**
   * Stops stocking the Variant, as when its Product leaves the Catalog; {@code false} when
   * Inventory doesn't stock it. Throws {@code StockReservedException} while Reservations hold some
   * of it. Its past Reservations stay.
   */
  boolean removeStock(String variantId);
}
