package com.ecomm.checkoutpricing.domain;

import com.ecomm.commons.money.Money;

/** A Cart line at the Price Catalog holds now: what becomes an Order Line. */
public record PricedLine(String variantId, int quantity, Money unitPrice) {

  public Money lineTotal() {
    return new Money(Math.multiplyExact(unitPrice.amountMinor(), quantity), unitPrice.currency());
  }
}
