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

  /** Whether it was newer than what is held, and so applied. */
  boolean apply(ProductPublished event);
}
