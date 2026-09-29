package com.ecomm.checkoutpricing.domain;

import com.ecomm.commons.money.Money;
import java.util.List;

/**
 * A Cart's lines, each re-priced from Catalog. An Order holds one currency, so every line must be
 * priced in the same one.
 */
public record PricedCart(List<PricedLine> lines) {

  public PricedCart {
    if (lines.isEmpty()) {
      throw new EmptyCartException();
    }
    lines = List.copyOf(lines);
    var currency = lines.getFirst().unitPrice().currency();
    if (lines.stream().anyMatch(line -> !line.unitPrice().currency().equals(currency))) {
      throw new MixedCurrencyException();
    }
  }

  /** The sum of the lines, before any discount or tax. */
  public Money subtotal() {
    var currency = lines.getFirst().unitPrice().currency();
    long sum = 0;
    for (var line : lines) {
      sum = Math.addExact(sum, line.lineTotal().amountMinor());
    }
    return new Money(sum, currency);
  }
}
