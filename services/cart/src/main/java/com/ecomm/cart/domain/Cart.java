package com.ecomm.cart.domain;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * A Customer's in-progress selection of Variants and quantities, each Variant at most once and in
 * Variant ID order. It holds no price: the Price that counts is always the one Catalog holds now. A
 * Cart lasts {@link #LIFETIME} after its last change, then is gone.
 */
public record Cart(List<CartItem> items) {

  public static final Duration LIFETIME = Duration.ofDays(7);

  public Cart {
    items = items.stream().sorted(Comparator.comparing(CartItem::variantId)).toList();
  }

  public static Cart empty() {
    return new Cart(List.of());
  }
}
