package com.ecomm.cart.adapter.in.web;

import com.ecomm.cart.domain.Cart;
import java.util.List;

record CartResponse(List<Item> items) {

  record Item(String variantId, int quantity) {}

  static CartResponse of(Cart cart) {
    return new CartResponse(
        cart.items().stream().map(i -> new Item(i.variantId(), i.quantity())).toList());
  }
}
