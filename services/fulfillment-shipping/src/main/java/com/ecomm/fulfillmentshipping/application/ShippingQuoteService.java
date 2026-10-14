package com.ecomm.fulfillmentshipping.application;

import com.ecomm.fulfillmentshipping.application.port.in.QuoteShippingUseCase;
import com.ecomm.fulfillmentshipping.application.port.out.ShippingProvider;
import com.ecomm.fulfillmentshipping.domain.Line;
import com.ecomm.fulfillmentshipping.domain.ShippingQuote;
import com.ecomm.fulfillmentshipping.domain.ShippingUnavailableException;
import com.ecomm.fulfillmentshipping.domain.Warehouses;
import java.util.Currency;
import java.util.List;

/**
 * Quotes from the Warehouse a Shipment to the destination would ship from, so what's quoted ships.
 */
public class ShippingQuoteService implements QuoteShippingUseCase {

  private final Warehouses warehouses;
  private final ShippingProvider carrier;

  public ShippingQuoteService(Warehouses warehouses, ShippingProvider carrier) {
    this.warehouses = warehouses;
    this.carrier = carrier;
  }

  @Override
  public ShippingQuote quote(String destinationCountry, List<Line> lines, Currency currency) {
    var origin = warehouses.assignedTo(destinationCountry);
    var options = carrier.quote(origin, destinationCountry, lines, currency);
    if (options.isEmpty()) {
      throw new ShippingUnavailableException(destinationCountry, currency.getCurrencyCode());
    }
    return new ShippingQuote(origin, options);
  }
}
