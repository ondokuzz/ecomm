package com.ecomm.returnswarranty.application;

import com.ecomm.commons.events.Versions;
import com.ecomm.returnswarranty.application.port.in.ProjectionUseCase;
import com.ecomm.returnswarranty.application.port.out.ProductStore;
import com.ecomm.returnswarranty.application.port.out.ShipmentStore;
import com.ecomm.returnswarranty.application.port.out.Transactions;
import com.ecomm.returnswarranty.domain.Product;
import com.ecomm.returnswarranty.domain.Shipment;

/**
 * Keeps each Shipment and Product if it is newer than what is held, reading and writing in one
 * transaction. The two projections are independent, so they may arrive in either order.
 */
public class ProjectionService implements ProjectionUseCase {

  private final ShipmentStore shipments;
  private final ProductStore products;
  private final Transactions transactions;

  public ProjectionService(
      ShipmentStore shipments, ProductStore products, Transactions transactions) {
    this.shipments = shipments;
    this.products = products;
    this.transactions = transactions;
  }

  @Override
  public boolean apply(Shipment shipment) {
    return transactions.inTransaction(
        () ->
            Versions.applyIfNewer(
                shipment.version(),
                shipments.versionOf(shipment.id()),
                Long::longValue,
                () -> shipments.save(shipment)));
  }

  @Override
  public boolean apply(Product product) {
    return transactions.inTransaction(
        () ->
            Versions.applyIfNewer(
                product.version(),
                products.versionOf(product.sku()),
                Long::longValue,
                () -> products.save(product)));
  }
}
