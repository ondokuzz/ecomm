package com.ecomm.commons.money;

import java.util.Currency;
import java.util.Objects;

/** An amount of money in a currency's minor unit (e.g. cents), never a floating-point value. */
public record Money(long amountMinor, Currency currency) {

  public Money {
    Objects.requireNonNull(currency, "currency");
  }

  public static Money of(long amountMinor, String currencyCode) {
    return new Money(amountMinor, Currency.getInstance(currencyCode));
  }
}
