package com.ecomm.returnswarranty.application;

import com.ecomm.commons.events.IntegrationEventPublisher;
import com.ecomm.commons.events.Versions;
import com.ecomm.returnswarranty.application.port.in.ProjectionUseCase;
import com.ecomm.returnswarranty.application.port.out.OrderStore;
import com.ecomm.returnswarranty.application.port.out.ProductStore;
import com.ecomm.returnswarranty.application.port.out.RmaEvent;
import com.ecomm.returnswarranty.application.port.out.RmaRepository;
import com.ecomm.returnswarranty.application.port.out.ShipmentStore;
import com.ecomm.returnswarranty.application.port.out.Transactions;
import com.ecomm.returnswarranty.domain.Order;
import com.ecomm.returnswarranty.domain.Product;
import com.ecomm.returnswarranty.domain.Rma;
import com.ecomm.returnswarranty.domain.Shipment;
import java.util.ArrayList;

/**
 * Keeps each Shipment, Product and Order if it is newer than what is held, reading and writing in
 * one transaction. The projections are independent, so they may arrive in any order. A Shipment or
 * an Order fills in, in the same transaction, what RMAs on its Order were missing from before it
 * was projected, their Warehouse or their Refund amount, and publishes each RMA it changed.
 */
public class ProjectionService implements ProjectionUseCase {

  private final ShipmentStore shipments;
  private final ProductStore products;
  private final OrderStore orders;
  private final RmaRepository rmas;
  private final IntegrationEventPublisher events;
  private final Transactions transactions;

  public ProjectionService(
      ShipmentStore shipments,
      ProductStore products,
      OrderStore orders,
      RmaRepository rmas,
      IntegrationEventPublisher events,
      Transactions transactions) {
    this.shipments = shipments;
    this.products = products;
    this.orders = orders;
    this.rmas = rmas;
    this.events = events;
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
                () -> {
                  shipments.save(shipment);
                  fillInWarehouses(shipment);
                }));
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

  @Override
  public boolean apply(Order order) {
    return transactions.inTransaction(
        () ->
            Versions.applyIfNewer(
                order.version(),
                orders.versionOf(order.id()),
                Long::longValue,
                () -> {
                  orders.save(order);
                  fillInRefundAmounts(order);
                }));
  }

  /**
   * Gives the Order's approved RMAs without a Warehouse the Shipment's. Each is locked and read
   * again first, as a step on it would be, so neither overwrites the other.
   */
  private void fillInWarehouses(Shipment shipment) {
    for (var found : rmas.ofOrder(shipment.orderId())) {
      if (found.lacksWarehouse()) {
        rmas.lock(found.id())
            .filter(Rma::lacksWarehouse)
            .ifPresent(rma -> backfilled(rma.withWarehouse(shipment.warehouseId())));
      }
    }
  }

  /** Gives the Order's RMAs without a Refund amount theirs, oldest first, each under its lock. */
  private void fillInRefundAmounts(Order order) {
    var ofOrder = new ArrayList<>(rmas.ofOrder(order.id()));
    for (var i = 0; i < ofOrder.size(); i++) {
      if (ofOrder.get(i).refundAmount() == null) {
        var locked = rmas.lock(ofOrder.get(i).id()).orElseThrow();
        if (locked.refundAmount() == null) {
          var filled = locked.withRefundAmount(order, ofOrder);
          backfilled(filled);
          ofOrder.set(i, filled);
        }
      }
    }
  }

  /** Keeps the RMA and publishes it, as {@code BACKFILLED}, in the projection's transaction. */
  private void backfilled(Rma rma) {
    rmas.update(rma);
    events.publish(RmaEvent.backfilled(rma));
  }

  @Override
  public boolean shipmentsNeedReplay() {
    return transactions.inTransaction(shipments::anyWithoutWarehouse);
  }
}
