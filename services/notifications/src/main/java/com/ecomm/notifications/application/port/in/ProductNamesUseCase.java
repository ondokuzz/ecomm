package com.ecomm.notifications.application.port.in;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.Map;

/** Keeps the newest name of each Product, for naming Order Lines, and its Variants' Prices. */
public interface ProductNamesUseCase {

  /**
   * A Product's event: when it was published, its name, and each Variant's axis values in their
   * order and its Price.
   */
  record ProductPublished(
      String sku,
      long version,
      Instant occurredAt,
      String name,
      Map<String, Map<String, String>> variantAxisValues,
      Map<String, Money> variantPrices) {}

  /**
   * Whether it was applied: newer than what is held, or the version held of a Product with a
   * Variant projected before Prices were kept, which so takes its Price.
   */
  boolean apply(ProductPublished event);

  /** Whether the topic should be read from the start: some Variant has no Price yet. */
  boolean needsReplay();
}
