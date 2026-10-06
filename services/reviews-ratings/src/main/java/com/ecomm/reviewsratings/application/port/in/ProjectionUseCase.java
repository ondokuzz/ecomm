package com.ecomm.reviewsratings.application.port.in;

import com.ecomm.reviewsratings.domain.OrderStatus;
import java.time.Instant;
import java.util.List;

/**
 * Keeps Reviews' copy of who bought what and of each Product's Variants, from Order Management's
 * and Catalog's events. Each change is applied only if its version is newer than the one held, so a
 * duplicate or stale event changes nothing.
 */
public interface ProjectionUseCase {

  /** An {@code order-management.order} event. */
  record OrderPublished(
      String orderId,
      long version,
      String customerId,
      OrderStatus status,
      Instant placedAt,
      List<String> variantIds) {}

  /** A {@code catalog.product} event: its Variants' IDs. */
  record ProductPublished(String sku, long version, List<String> variantIds) {}

  /**
   * @return whether it was applied, rather than ignored as a duplicate or stale
   */
  boolean apply(OrderPublished order);

  boolean apply(ProductPublished product);
}
