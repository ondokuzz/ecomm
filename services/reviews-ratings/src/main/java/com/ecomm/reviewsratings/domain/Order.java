package com.ecomm.reviewsratings.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** An Order as Reviews keeps it: who placed it, its Status, and the Variants it bought. */
public record Order(
    String orderId,
    long version,
    String customerId,
    OrderStatus status,
    Instant placedAt,
    List<String> variantIds) {

  public Order {
    variantIds = List.copyOf(variantIds);
  }

  /**
   * The Variant of a Product that a Customer bought, from their latest Order that counts and has
   * one of {@code productVariants}; empty if none does, so they may not review it.
   */
  public static Optional<String> variantBought(
      Collection<Order> orders, Set<String> productVariants) {
    return orders.stream()
        .filter(order -> order.status().counts())
        .sorted(Comparator.comparing(Order::placedAt).reversed())
        .flatMap(order -> order.variantIds().stream())
        .filter(productVariants::contains)
        .findFirst();
  }
}
