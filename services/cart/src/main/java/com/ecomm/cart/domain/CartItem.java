package com.ecomm.cart.domain;

/** How many units of one Variant a Cart holds; always at least one. */
public record CartItem(String variantId, int quantity) {

  public CartItem {
    if (variantId == null || variantId.isBlank()) {
      throw new InvalidCartItemException("a Cart item needs a variantId");
    }
    if (quantity <= 0) {
      throw new InvalidCartItemException("quantity must be a positive integer");
    }
  }
}
