package com.ecomm.inventory.domain;

import java.util.List;

/** Inventory holds no stock for these Variants. */
public class UnknownVariantException extends RuntimeException {

  private final List<String> variantIds;

  public UnknownVariantException(List<String> variantIds) {
    super("No stock for Variant " + String.join(", ", variantIds));
    this.variantIds = List.copyOf(variantIds);
  }

  public List<String> variantIds() {
    return variantIds;
  }
}
