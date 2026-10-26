package com.ecomm.recommendations.application.port.in;

import com.ecomm.recommendations.domain.OrderStatus;
import java.time.Instant;
import java.util.List;

/**
 * Keeps Recommendations' copy of which Variants each Order bought, of each Product's Category and
 * Variants, of each Category's name, and of whether each Variant is in Stock, from Order
 * Management's, Catalog's and Inventory's events. Each change is applied only if its version is
 * newer than the one held, so a duplicate or stale event changes nothing.
 */
public interface ProjectionUseCase {

  /** An {@code order-management.order} event. */
  record OrderPublished(
      String orderId,
      long version,
      OrderStatus status,
      Instant placedAt,
      List<String> variantIds) {}

  /**
   * A {@code catalog.product} event: whether it was removed, its Category's slug, and its Variants'
   * IDs.
   */
  record ProductPublished(
      String sku, long version, boolean removed, String category, List<String> variantIds) {}

  /** A {@code catalog.category} event: its name, and whether it was removed. */
  record CategoryPublished(String slug, long version, String name, boolean removed) {}

  /** An {@code inventory.stock} event: whether Inventory stocks the Variant, and how many. */
  record StockPublished(String variantId, long version, boolean stocked, int available) {}

  /**
   * @return whether it was applied, rather than ignored as a duplicate or stale
   */
  boolean apply(OrderPublished order);

  /**
   * As the others, except that a Product kept before Recommendations kept Categories is applied
   * again at the version held, to learn its Category.
   */
  boolean apply(ProductPublished product);

  boolean apply(CategoryPublished category);

  boolean apply(StockPublished stock);

  /**
   * Whether any Product held was kept before Recommendations kept Categories, so {@code
   * catalog.product} is to be read again from the start.
   */
  boolean productsNeedReplay();
}
