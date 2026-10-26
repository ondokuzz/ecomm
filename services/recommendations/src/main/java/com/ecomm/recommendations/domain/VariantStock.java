package com.ecomm.recommendations.domain;

/** Whether a Variant is in Stock, as Recommendations keeps it, up to {@code version}. */
public record VariantStock(String variantId, long version, boolean inStock) {

  /**
   * A Variant is in Stock when Inventory stocks it and has some available, as in Search. One with
   * no Stock event yet is out of Stock.
   */
  public static VariantStock of(String variantId, long version, boolean stocked, int available) {
    return new VariantStock(variantId, version, stocked && available > 0);
  }
}
