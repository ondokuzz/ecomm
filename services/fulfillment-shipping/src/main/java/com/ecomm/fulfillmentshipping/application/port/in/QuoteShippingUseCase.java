package com.ecomm.fulfillmentshipping.application.port.in;

import com.ecomm.fulfillmentshipping.domain.Line;
import com.ecomm.fulfillmentshipping.domain.ShippingQuote;
import com.ecomm.fulfillmentshipping.domain.ShippingUnavailableException;
import java.util.Currency;
import java.util.List;

public interface QuoteShippingUseCase {

  /**
   * The Warehouse assigned to {@code destinationCountry}, and the Shipping options from it.
   *
   * @throws ShippingUnavailableException when there are none
   */
  ShippingQuote quote(String destinationCountry, List<Line> lines, Currency currency);
}
