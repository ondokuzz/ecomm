package com.ecomm.checkoutpricing.domain;

import java.util.List;

/** Catalog has no Price for these Variants in the Cart: their Product is gone. */
public class UnknownVariantsException extends RuntimeException {

  private final List<String> variantIds;

  public UnknownVariantsException(List<String> variantIds) {
    super("These Variants are no longer in the Catalog: " + String.join(", ", variantIds));
    this.variantIds = List.copyOf(variantIds);
  }

  public List<String> variantIds() {
    return variantIds;
  }
}
