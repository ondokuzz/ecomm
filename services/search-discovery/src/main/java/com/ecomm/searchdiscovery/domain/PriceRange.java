package com.ecomm.searchdiscovery.domain;

import java.util.Currency;
import java.util.Objects;

/**
 * Prices in one Currency, in its Minor unit, inclusive at both ends; a null end is open. A Product
 * is in range when any of its Variants' Prices is.
 */
public record PriceRange(Currency currency, Long min, Long max) {

  public PriceRange {
    Objects.requireNonNull(currency, "currency");
    if (min != null && max != null && min > max) {
      throw new InvalidSearchException("price's min is above its max");
    }
  }

  boolean contains(SearchableProduct product) {
    return product.variants().stream()
        .map(SearchableVariant::price)
        .anyMatch(
            p ->
                p.currency().equals(currency)
                    && (min == null || p.amountMinor() >= min)
                    && (max == null || p.amountMinor() <= max));
  }
}
