package com.ecomm.fulfillmentshipping.application.port.in;

import com.ecomm.fulfillmentshipping.domain.Line;
import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.ProductsNotKnownYetException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.ShipmentExistsException;
import com.ecomm.fulfillmentshipping.domain.ShippingAddress;
import com.ecomm.fulfillmentshipping.domain.ShippingOption;
import java.util.List;

public interface CreateShipmentUseCase {

  /** An Order to ship: its lines, to its Shipping address, by its Shipping option. */
  record NewShipment(
      String orderId,
      String customerId,
      List<Line> lines,
      ShippingAddress shippingAddress,
      ShippingOption shippingOption) {}

  /**
   * The Order's Shipment, awaiting pick from the Warehouse assigned to its address.
   *
   * @throws ShipmentExistsException when the Order already has one
   * @throws ProductsNotKnownYetException when a Variant's Product isn't projected yet
   */
  Shipment create(NewShipment order);
}
