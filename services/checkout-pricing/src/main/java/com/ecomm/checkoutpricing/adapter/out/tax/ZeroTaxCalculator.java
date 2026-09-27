package com.ecomm.checkoutpricing.adapter.out.tax;

import com.ecomm.checkoutpricing.application.port.out.TaxCalculator;
import com.ecomm.checkoutpricing.domain.PricedCart;
import com.ecomm.commons.money.Money;
import org.springframework.stereotype.Component;

/** No tax at all: the stand-in until a market's tax rules are modelled (ADR 0005). */
@Component
class ZeroTaxCalculator implements TaxCalculator {

  @Override
  public Money tax(PricedCart cart) {
    return new Money(0, cart.subtotal().currency());
  }
}
