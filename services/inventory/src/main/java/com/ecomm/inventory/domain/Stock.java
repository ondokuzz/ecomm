package com.ecomm.inventory.domain;

/** How many units of a Variant are available to sell. Never negative. */
public record Stock(String variantId, int quantity) {

  public Stock {
    if (quantity < 0) {
      throw new IllegalArgumentException("Stock of " + variantId + " cannot be negative");
    }
  }

  public boolean covers(int units) {
    return units <= quantity;
  }

  /** Callers check {@link #covers} first; taking more than there is throws. */
  public Stock decrementBy(int units) {
    return new Stock(variantId, quantity - units);
  }
}
