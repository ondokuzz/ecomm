package com.ecomm.searchdiscovery.domain;

/** A Variant's Stock as Inventory last published it, at {@code version}. */
public record VariantStock(String variantId, long version, long available, boolean stocked) {

  /** Whether there is any of it to sell. */
  public boolean inStock() {
    return stocked && available > 0;
  }
}
