package com.ecomm.inventory.domain;

import java.util.List;

/** A batch needs more of these Variants than is available. */
public class InsufficientStockException extends RuntimeException {

  private final List<String> variantIds;

  public InsufficientStockException(List<String> variantIds) {
    super("Not enough available Stock of " + String.join(", ", variantIds));
    this.variantIds = List.copyOf(variantIds);
  }

  public List<String> variantIds() {
    return variantIds;
  }
}
