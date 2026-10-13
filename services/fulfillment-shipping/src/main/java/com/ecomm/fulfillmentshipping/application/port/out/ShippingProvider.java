package com.ecomm.fulfillmentshipping.application.port.out;

import com.ecomm.fulfillmentshipping.domain.Line;
import com.ecomm.fulfillmentshipping.domain.ShippingOption;
import com.ecomm.fulfillmentshipping.domain.Warehouse;
import java.util.Currency;
import java.util.List;

/**
 * A Carrier (ADR 0005). Booking a Shipment and its Tracking events join quoting once Shipments
 * exist.
 */
public interface ShippingProvider {

  /**
   * The Shipping options for {@code lines} from {@code origin} to {@code destinationCountry},
   * priced in {@code currency}; none when the Carrier doesn't ship there or doesn't price in it.
   */
  List<ShippingOption> quote(
      Warehouse origin, String destinationCountry, List<Line> lines, Currency currency);
}
