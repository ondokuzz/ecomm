package com.ecomm.inventory.application.port.in;

import com.ecomm.inventory.domain.Stock;
import com.ecomm.inventory.domain.StockDecrement;
import java.util.List;

public interface DecrementStockUseCase {

  /**
   * Applies the whole batch, or nothing: throws {@code UnknownVariantException} or {@code
   * InsufficientStockException} leaving all stock unchanged. Returns the new stock of every Variant
   * in the batch.
   */
  List<Stock> decrement(StockDecrement decrement);
}
