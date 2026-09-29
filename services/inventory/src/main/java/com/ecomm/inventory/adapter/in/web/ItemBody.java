package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.domain.InvalidStockRequestException;
import com.ecomm.inventory.domain.StockBatch;
import java.util.List;

/** One Variant and a quantity in a batch: {@code {"variantId", "quantity"}}. */
record ItemBody(String variantId, Integer quantity) {

  StockBatch.Line toLine() {
    if (quantity == null) {
      throw new InvalidStockRequestException("every item needs a quantity");
    }
    return new StockBatch.Line(variantId, quantity);
  }

  static StockBatch toBatch(List<ItemBody> items) {
    return StockBatch.of(
        items == null ? null : items.stream().map(i -> i == null ? null : i.toLine()).toList());
  }
}
