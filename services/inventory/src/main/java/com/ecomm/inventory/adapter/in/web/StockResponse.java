package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.domain.Stock;

/** {@code quantity} is what is available to sell: {@code onHand} less what is {@code reserved}. */
record StockResponse(String variantId, int quantity, int onHand, int reserved) {

  static StockResponse of(Stock stock) {
    return new StockResponse(
        stock.variantId(), stock.available(), stock.onHand(), stock.reserved());
  }
}
