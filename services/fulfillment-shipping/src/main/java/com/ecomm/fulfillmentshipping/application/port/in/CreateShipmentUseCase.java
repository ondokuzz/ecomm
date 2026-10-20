package com.ecomm.fulfillmentshipping.application.port.in;

import com.ecomm.fulfillmentshipping.domain.Line;
import com.ecomm.fulfillmentshipping.domain.Shipment;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.ProductsNotKnownYetException;
import com.ecomm.fulfillmentshipping.domain.ShipmentExceptions.ShipmentExistsException;
import com.ecomm.fulfillmentshipping.domain.ShipmentKind;
import com.ecomm.fulfillmentshipping.domain.ShippingAddress;
import com.ecomm.fulfillmentshipping.domain.ShippingOption;
import java.util.List;

public interface CreateShipmentUseCase {

  /**
   * What to ship: an Order's lines, or with {@code kind} {@code REPLACEMENT} the units replacing
   * those of the RMA {@code rmaId} (null for an Order), to its Shipping address, by its Shipping
   * option.
   */
  record NewShipment(
      ShipmentKind kind,
      String rmaId,
      String orderId,
      String customerId,
      List<Line> lines,
      ShippingAddress shippingAddress,
      ShippingOption shippingOption) {}

  /**
   * The Shipment, awaiting pick from the Warehouse assigned to its address.
   *
   * @throws ShipmentExistsException when the Order already has its {@code ORDER} Shipment, or the
   *     RMA its replacement
   * @throws ProductsNotKnownYetException when a Variant's Product isn't projected yet
   */
  Shipment create(NewShipment order);
}
