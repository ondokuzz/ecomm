package com.ecomm.inventory.adapter.in.web;

import com.ecomm.inventory.domain.StockBatch;
import java.util.List;

/** Units to take off several Variants' stock: {@code {"items": [{"variantId", "quantity"}]}}. */
record DecrementRequest(List<ItemBody> items) {

  StockBatch toDecrement() {
    return ItemBody.toBatch(items);
  }
}
