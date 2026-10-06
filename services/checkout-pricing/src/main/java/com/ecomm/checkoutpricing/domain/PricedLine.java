package com.ecomm.checkoutpricing.domain;

import com.ecomm.commons.money.Money;

/**
 * A Cart line at the Price Catalog holds now, with its Product's SKU and Category, which decide the
 * Campaigns it gets: what becomes an Order Line.
 */
public record PricedLine(
    String variantId, String sku, String category, int quantity, Money unitPrice) {

  public Money lineTotal() {
    return new Money(Math.multiplyExact(unitPrice.amountMinor(), quantity), unitPrice.currency());
  }
}
