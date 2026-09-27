package com.ecomm.checkoutpricing.domain;

import java.util.List;

/** Inventory doesn't have enough Stock of these Variants for the Cart. */
public class OutOfStockException extends RuntimeException {

  private final List<String> variantIds;

  public OutOfStockException(List<String> variantIds) {
    super("Not enough stock of: " + String.join(", ", variantIds));
    this.variantIds = List.copyOf(variantIds);
  }

  public List<String> variantIds() {
    return variantIds;
  }
}
