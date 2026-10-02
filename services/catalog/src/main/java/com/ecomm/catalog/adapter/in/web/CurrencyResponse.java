package com.ecomm.catalog.adapter.in.web;

import java.util.Currency;

/**
 * A currency a Price can be in: its ISO 4217 code, and how many digits its minor unit has (2 for
 * EUR, 0 for JPY), which is how clients turn an {@code amountMinor} into a decimal and back.
 */
record CurrencyResponse(String code, int minorDigits) {

  static CurrencyResponse of(Currency currency) {
    return new CurrencyResponse(currency.getCurrencyCode(), currency.getDefaultFractionDigits());
  }
}
