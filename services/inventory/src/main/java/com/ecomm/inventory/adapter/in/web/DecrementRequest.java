package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.domain.InvalidStockDecrementException;
import com.ecomm.inventory.domain.StockDecrement;
import java.util.List;

/** Units to take off several Variants' stock: {@code {"items": [{"variantId", "quantity"}]}}. */
record DecrementRequest(List<Item> items) {

  record Item(String variantId, Integer quantity) {

    StockDecrement.Line toLine() {
      if (quantity == null) {
        throw new InvalidStockDecrementException("every item needs a quantity");
      }
      return new StockDecrement.Line(variantId, quantity);
    }
  }

  StockDecrement toDecrement() {
    return StockDecrement.of(
        items == null ? null : items.stream().map(i -> i == null ? null : i.toLine()).toList());
  }
}
