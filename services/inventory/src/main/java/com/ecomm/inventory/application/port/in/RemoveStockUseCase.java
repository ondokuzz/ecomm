package com.ecomm.inventory.application.port.in;

public interface RemoveStockUseCase {

  /**
   * Stops stocking the Variant, as when its Product leaves the Catalog; {@code false} when
   * Inventory doesn't stock it. Throws {@code StockReservedException} while Reservations hold some
   * of it. Its past Reservations and its movements stay; its On-hand leaves as an {@code ADJUSTED}
   * movement, so stocking it again starts from 0.
   */
  boolean removeStock(String variantId);
}
