package com.ecomm.recommendations.domain;

import java.time.Instant;
import java.util.Set;

/**
 * An Order as Recommendations keeps it: whether it counts towards what was bought together, when it
 * was placed, and the Variants it bought.
 */
public record Order(
    String orderId, long version, boolean counts, Instant placedAt, Set<String> variantIds) {

  public Order {
    variantIds = Set.copyOf(variantIds);
  }
}
