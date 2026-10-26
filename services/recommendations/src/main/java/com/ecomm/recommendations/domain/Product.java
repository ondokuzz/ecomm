package com.ecomm.recommendations.domain;

import java.util.Set;

/**
 * A Product as Recommendations keeps it, up to {@code version}: whether it has left the Catalog,
 * its Category's slug, and its Variants.
 */
public record Product(
    String sku, long version, boolean removed, String category, Set<String> variantIds) {

  public Product {
    variantIds = Set.copyOf(variantIds);
  }
}
