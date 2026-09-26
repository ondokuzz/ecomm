package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.domain.Stock;

record StockResponse(String variantId, int quantity) {

  static StockResponse of(Stock stock) {
    return new StockResponse(stock.variantId(), stock.quantity());
  }
}
