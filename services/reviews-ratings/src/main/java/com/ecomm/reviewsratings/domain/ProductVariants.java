package com.ecomm.reviewsratings.domain;

import java.util.Set;

/** Every Variant a Product has had in Catalog's events, up to {@code version}. */
public record ProductVariants(String sku, long version, Set<String> variantIds) {

  public ProductVariants {
    variantIds = Set.copyOf(variantIds);
  }
}
