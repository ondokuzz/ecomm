package com.ecomm.returnswarranty.domain;

/** One line of an Order: a Variant, how many units, and the Price of each, in Minor units. */
public record OrderLine(String variantId, int quantity, long unitPriceMinor) {

  long subtotalMinor() {
    return quantity * unitPriceMinor;
  }
}
