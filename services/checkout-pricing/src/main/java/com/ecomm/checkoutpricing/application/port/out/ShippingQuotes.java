package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.checkoutpricing.application.port.in.ShippingUnavailableException;
import com.ecomm.checkoutpricing.domain.PricedLine;
import com.ecomm.checkoutpricing.domain.ShippingAddress;
import com.ecomm.checkoutpricing.domain.ShippingOption;
import java.util.Currency;
import java.util.List;

/** Fulfillment & Shipping, which quotes Shipping options from the Warehouse that will ship. */
public interface ShippingQuotes {

  /**
   * The Shipping options for sending {@code lines} to {@code destination}, priced in {@code
   * currency}; none when the Carrier doesn't serve that country or currency.
   *
   * @throws ShippingUnavailableException when Fulfillment & Shipping can't quote
   */
  List<ShippingOption> quote(
      ShippingAddress destination, List<PricedLine> lines, Currency currency);
}
