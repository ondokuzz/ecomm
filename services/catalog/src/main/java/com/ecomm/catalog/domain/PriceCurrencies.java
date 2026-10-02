package com.ecomm.catalog.domain;

import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

/**
 * The currencies a Price can be in: ISO 4217's, as the JDK knows them, that have a minor unit.
 * Codes such as {@code XXX} (no currency) or {@code XAU} (gold) have none, so nothing can be priced
 * in them. Clients read and write Money by this list rather than by their own currency data, which
 * disagrees with ISO 4217 on some minor units, such as HUF's.
 */
public final class PriceCurrencies {

  private static final List<Currency> ALL =
      Currency.getAvailableCurrencies().stream()
          .filter(PriceCurrencies::hasMinorUnit)
          .sorted(Comparator.comparing(Currency::getCurrencyCode))
          .toList();

  private PriceCurrencies() {}

  /** Every currency a Price can be in, ordered by code. */
  public static List<Currency> all() {
    return ALL;
  }

  /** The currency a Price can be in with this ISO 4217 code, matched exactly. */
  public static Optional<Currency> of(String code) {
    return ALL.stream().filter(c -> c.getCurrencyCode().equals(code)).findFirst();
  }

  static boolean hasMinorUnit(Currency currency) {
    return currency.getDefaultFractionDigits() >= 0;
  }
}
