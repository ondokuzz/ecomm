package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.domain.PricedCart;
import com.ecomm.commons.money.Money;

/**
 * The tax on a priced Cart, kept behind a port so market-specific tax rules can replace the
 * zero-tax default without touching checkout (ADR 0005).
 */
public interface TaxCalculator {

  /** The tax owed on {@code cart} once {@code discount} is taken off it, in its currency. */
  Money tax(PricedCart cart, Money discount);
}
