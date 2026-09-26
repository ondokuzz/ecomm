package com.ecomm.inventory.domain;

import java.util.List;

/** A decrement would take these Variants' stock below zero. */
public class InsufficientStockException extends RuntimeException {

  private final List<String> variantIds;

  public InsufficientStockException(List<String> variantIds) {
    super("Not enough stock of " + String.join(", ", variantIds));
    this.variantIds = List.copyOf(variantIds);
  }

  public List<String> variantIds() {
    return variantIds;
  }
}
