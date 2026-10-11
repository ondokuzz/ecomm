package com.ecomm.recommendations.application;

import com.ecomm.commons.events.Versions;
import com.ecomm.recommendations.application.port.in.ProjectionUseCase;
import com.ecomm.recommendations.application.port.out.OrderStore;
import com.ecomm.recommendations.application.port.out.ProductStore;
import com.ecomm.recommendations.application.port.out.Transactions;
import com.ecomm.recommendations.domain.Order;
import com.ecomm.recommendations.domain.Product;
import java.util.HashSet;

/** Applies each event if it is newer than what is held, reading and writing in one transaction. */
public class ProjectionService implements ProjectionUseCase {

  private final OrderStore orders;
  private final ProductStore products;
  private final Transactions transactions;

  public ProjectionService(OrderStore orders, ProductStore products, Transactions transactions) {
    this.orders = orders;
    this.products = products;
    this.transactions = transactions;
  }

  @Override
  public boolean apply(OrderPublished event) {
    return transactions.inTransaction(
        () ->
            Versions.applyIfNewer(
                event.version(),
                orders.versionOf(event.orderId()),
                Long::longValue,
                () ->
                    orders.save(
                        new Order(
                            event.orderId(),
                            event.version(),
                            event.status().counts(),
                            event.placedAt(),
                            new HashSet<>(event.variantIds())))));
  }

  /**
   * A Product keeps every Variant it has ever had, so an Order for a Variant Catalog has since
   * dropped still counts as buying its Product.
   */
  @Override
  public boolean apply(ProductPublished event) {
    return transactions.inTransaction(
        () ->
            Versions.applyIfNewer(
                event.version(),
                products.versionOf(event.sku()),
                Long::longValue,
                () ->
                    products.save(
                        new Product(
                            event.sku(),
                            event.version(),
                            event.removed(),
                            new HashSet<>(event.variantIds())))));
  }
}
