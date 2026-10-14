package com.ecomm.returnswarranty.application.port.in;

import com.ecomm.returnswarranty.domain.Product;
import com.ecomm.returnswarranty.domain.Shipment;

/** Applies Fulfillment & Shipping's Shipments and Catalog's Products to the projections. */
public interface ProjectionUseCase {

  /** Keeps the Shipment if it is newer than what is held; whether it was. */
  boolean apply(Shipment shipment);

  /** Keeps the Product if it is newer than what is held; whether it was. */
  boolean apply(Product product);
}
