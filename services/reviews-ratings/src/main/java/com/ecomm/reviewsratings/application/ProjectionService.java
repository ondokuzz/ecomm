package com.ecomm.reviewsratings.application;

import com.ecomm.commons.events.Versions;
import com.ecomm.reviewsratings.application.port.in.ProjectionUseCase;
import com.ecomm.reviewsratings.application.port.out.OrderStore;
import com.ecomm.reviewsratings.application.port.out.ProductStore;
import com.ecomm.reviewsratings.domain.Order;
import com.ecomm.reviewsratings.domain.ProductVariants;
import java.util.HashSet;

/**
 * Applies each event if it is newer than what is held. One aggregate's events arrive on one
 * partition, which one consumer reads in order, so reading then writing its one document needs no
 * transaction.
 */
public class ProjectionService implements ProjectionUseCase {

  private final OrderStore orders;
  private final ProductStore products;

  public ProjectionService(OrderStore orders, ProductStore products) {
    this.orders = orders;
    this.products = products;
  }

  @Override
  public boolean apply(OrderPublished event) {
    return Versions.applyIfNewer(
        event.version(),
        orders.find(event.orderId()),
        Order::version,
        () ->
            orders.save(
                new Order(
                    event.orderId(),
                    event.version(),
                    event.customerId(),
                    event.status(),
                    event.placedAt(),
                    event.variantIds())));
  }

  /**
   * A Product keeps every Variant it has ever had, so a Customer who paid for one that Catalog has
   * since dropped, or whose Product was removed, may still review it.
   */
  @Override
  public boolean apply(ProductPublished event) {
    var stored = products.find(event.sku());
    return Versions.applyIfNewer(
        event.version(),
        stored,
        ProductVariants::version,
        () -> {
          var variantIds = new HashSet<>(event.variantIds());
          stored.ifPresent(p -> variantIds.addAll(p.variantIds()));
          products.save(new ProductVariants(event.sku(), event.version(), variantIds));
        });
  }
}
