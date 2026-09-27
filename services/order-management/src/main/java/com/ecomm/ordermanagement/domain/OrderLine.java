package com.ecomm.ordermanagement.domain;

import com.ecomm.commons.money.Money;

/**
 * One Variant, its quantity, and its unit price captured at checkout. Throws {@link
 * InvalidOrderException} unless all three are valid.
 */
public record OrderLine(String variantId, int quantity, Money unitPrice) {

  /** The longest Variant ID an Order Line can hold. */
  public static final int MAX_VARIANT_ID_LENGTH = 64;

  public OrderLine {
    if (variantId == null || variantId.isBlank()) {
      throw new InvalidOrderException("every line needs a variantId");
    }
    if (variantId.length() > MAX_VARIANT_ID_LENGTH) {
      throw new InvalidOrderException(
          "a variantId is at most " + MAX_VARIANT_ID_LENGTH + " characters");
    }
    if (quantity <= 0) {
      throw new InvalidOrderException("the quantity for " + variantId + " must be positive");
    }
    if (unitPrice == null) {
      throw new InvalidOrderException("the line for " + variantId + " needs a unitPrice");
    }
    if (unitPrice.amountMinor() < 0) {
      throw new InvalidOrderException("the unitPrice for " + variantId + " can't be negative");
    }
  }

  /** The unit price times the quantity. */
  public long subtotalMinor() {
    try {
      return Math.multiplyExact(unitPrice.amountMinor(), (long) quantity);
    } catch (ArithmeticException e) {
      throw new InvalidOrderException("the line for " + variantId + " is too large");
    }
  }
}
