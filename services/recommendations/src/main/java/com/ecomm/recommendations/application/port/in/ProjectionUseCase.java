package com.ecomm.recommendations.application.port.in;

import com.ecomm.recommendations.domain.OrderStatus;
import java.time.Instant;
import java.util.List;

/**
 * Keeps Recommendations' copy of which Variants each Order bought and of each Product's Variants,
 * from Order Management's and Catalog's events. Each change is applied only if its version is newer
 * than the one held, so a duplicate or stale event changes nothing.
 */
public interface ProjectionUseCase {

  /** An {@code order-management.order} event. */
  record OrderPublished(
      String orderId,
      long version,
      OrderStatus status,
      Instant placedAt,
      List<String> variantIds) {}

  /** A {@code catalog.product} event: whether it was removed, and its Variants' IDs. */
  record ProductPublished(String sku, long version, boolean removed, List<String> variantIds) {}

  /**
   * @return whether it was applied, rather than ignored as a duplicate or stale
   */
  boolean apply(OrderPublished order);

  boolean apply(ProductPublished product);
}
