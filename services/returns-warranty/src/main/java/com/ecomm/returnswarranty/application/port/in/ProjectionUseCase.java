package com.ecomm.returnswarranty.application.port.in;

import com.ecomm.returnswarranty.domain.Order;
import com.ecomm.returnswarranty.domain.Product;
import com.ecomm.returnswarranty.domain.Shipment;

/**
 * Applies Fulfillment & Shipping's Shipments, Catalog's Products and Order Management's Orders to
 * the projections.
 */
public interface ProjectionUseCase {

  /**
   * Keeps the Shipment if it is newer than what is held, or if what is held has no Warehouse;
   * whether it was. Approved RMAs on its Order without a Warehouse are given its, and published.
   */
  boolean apply(Shipment shipment);

  /** Keeps the Product if it is newer than what is held; whether it was. */
  boolean apply(Product product);

  /**
   * Keeps the Order if it is newer than what is held; whether it was. RMAs on it without a Refund
   * amount, requested before it was projected, are given theirs, and published.
   */
  boolean apply(Order order);

  /**
   * Whether the Shipment projection must be replayed from the start: some Shipment was kept before
   * Warehouses were.
   */
  boolean shipmentsNeedReplay();
}
