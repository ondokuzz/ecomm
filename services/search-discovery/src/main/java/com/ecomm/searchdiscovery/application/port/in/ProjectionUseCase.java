package com.ecomm.searchdiscovery.application.port.in;

import com.ecomm.searchdiscovery.domain.AttributeDefinition;
import com.ecomm.searchdiscovery.domain.SearchableVariant;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Keeps Search's copy of the Catalog and its Stock, from Catalog's and Inventory's events. Each
 * change is applied only if its version is newer than the one held, so a duplicate or stale event
 * changes nothing.
 */
public interface ProjectionUseCase {

  /**
   * A {@code catalog.product} event: the Product's whole state, published at {@code occurredAt}.
   */
  record ProductPublished(
      String sku,
      long version,
      Instant occurredAt,
      String name,
      String description,
      String category,
      Map<String, String> attributes,
      List<String> images,
      List<SearchableVariant> variants,
      boolean removed) {}

  /** A {@code catalog.category} event. */
  record CategoryPublished(
      String slug,
      long version,
      String name,
      List<AttributeDefinition> attributes,
      boolean removed) {}

  /** An {@code inventory.stock} event. */
  record StockPublished(String variantId, long version, long available, boolean stocked) {}

  /**
   * @return whether it was applied, rather than ignored as a duplicate or stale
   */
  boolean apply(ProductPublished product);

  boolean apply(CategoryPublished category);

  boolean apply(StockPublished stock);
}
