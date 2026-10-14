package com.ecomm.fulfillmentshipping.application.port.out;

import com.ecomm.fulfillmentshipping.domain.Booking;
import com.ecomm.fulfillmentshipping.domain.Line;
import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShippingOption;
import com.ecomm.fulfillmentshipping.domain.Warehouse;
import java.util.Currency;
import java.util.List;

/**
 * A Carrier (ADR 0005): it quotes, books Shipments, and reports each booked Shipment's Tracking
 * events as Carrier webhooks to {@code POST /webhooks/carrier}.
 */
public interface ShippingProvider {

  /**
   * The Shipping options for {@code lines} from {@code origin} to {@code destinationCountry},
   * priced in {@code currency}; none when the Carrier doesn't ship there or doesn't price in it.
   */
  List<ShippingOption> quote(
      Warehouse origin, String destinationCountry, List<Line> lines, Currency currency);

  /**
   * Hands {@code shipment} to the Carrier. A repeat of {@code idempotencyKey} is answered with the
   * first booking, and books nothing more.
   */
  Booking book(Shipment shipment, String idempotencyKey);
}
