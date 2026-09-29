package com.ecomm.catalog.domain;

import java.util.List;

/**
 * An update left out some of a Product's Variant IDs. A Variant ID never changes and a Variant is
 * never removed on its own, since Carts, Orders and Stock go on naming it.
 */
public class VariantIdsDroppedException extends RuntimeException {

  public VariantIdsDroppedException(String sku, List<String> variantIds) {
    super(
        "Product "
            + sku
            + " must keep its Variant "
            + (variantIds.size() == 1 ? "ID " : "IDs ")
            + String.join(", ", variantIds)
            + "; a Variant ID never changes, and a Variant is only removed with its Product");
  }
}
