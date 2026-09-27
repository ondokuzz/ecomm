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

  /** The sum of the lines, before tax. */
  public Money subtotal() {
    var currency = lines.getFirst().unitPrice().currency();
    long sum = 0;
    for (var line : lines) {
      sum = Math.addExact(sum, line.lineTotal().amountMinor());
    }
    return new Money(sum, currency);
  }

  /** What the Customer pays: the subtotal plus {@code tax}, which must be in the same currency. */
  public Money total(Money tax) {
    var subtotal = subtotal();
    if (!tax.currency().equals(subtotal.currency())) {
      throw new MixedCurrencyException();
    }
    return new Money(Math.addExact(subtotal.amountMinor(), tax.amountMinor()), subtotal.currency());
  }
}
