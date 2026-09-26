package com.ecomm.catalog.adapter.in.web;

import com.ecomm.commons.money.Money;

/** A Price as clients see it: an amount in the currency's minor unit, and an ISO 4217 code. */
record PriceResponse(long amountMinor, String currency) {

  static PriceResponse of(Money money) {
    return new PriceResponse(money.amountMinor(), money.currency().getCurrencyCode());
  }
}
