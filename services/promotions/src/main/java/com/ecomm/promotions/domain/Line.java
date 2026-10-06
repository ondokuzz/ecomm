package com.ecomm.promotions.domain;

import com.ecomm.commons.money.Money;

/**
 * One line of a Checkout Session, as Checkout sends it to be discounted: a Variant, its Product's
 * SKU and Category, how many, and the unit Price captured when the session started. Throws {@link
 * InvalidPromotionException}, naming the field under {@code lines[i]} as {@code field}, unless
 * every field is valid.
 */
public record Line(String variantId, String sku, String category, int quantity, Money unitPrice) {

  public Line {
    if (variantId == null || variantId.isBlank()) {
      throw new InvalidPromotionException("variantId", "is needed");
    }
    if (sku == null || sku.isBlank()) {
      throw new InvalidPromotionException("sku", "is needed");
    }
    if (category == null || category.isBlank()) {
      throw new InvalidPromotionException("category", "is needed");
    }
    if (quantity < 1) {
      throw new InvalidPromotionException("quantity", "must be 1 or more");
    }
    if (unitPrice == null) {
      throw new InvalidPromotionException("unitPrice", "is needed");
    }
    if (unitPrice.amountMinor() < 0) {
      throw new InvalidPromotionException("unitPrice", "can't be negative");
    }
  }

  /** The unit Price times the quantity. */
  public long totalMinor() {
    try {
      return Math.multiplyExact(unitPrice.amountMinor(), quantity);
    } catch (ArithmeticException e) {
      throw new InvalidPromotionException("unitPrice", "times the quantity is too large");
    }
  }
}
