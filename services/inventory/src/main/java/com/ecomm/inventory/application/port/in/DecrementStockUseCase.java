package com.ecomm.inventory.application.port.in;

import com.ecomm.inventory.domain.Stock;
import com.ecomm.inventory.domain.StockBatch;
import java.util.List;

public interface DecrementStockUseCase {

  /**
   * Takes the whole batch off on-hand Stock, or nothing: throws {@code UnknownVariantException} or
   * {@code InsufficientStockException} leaving all Stock unchanged. Only available Stock can be
   * taken, never what Reservations hold. Returns the new Stock of every Variant in the batch.
   */
  List<Stock> decrement(StockBatch decrement);
}
