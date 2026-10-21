package com.ecomm.notifications.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * The projection of Catalog's Products that names Order Lines, since an Order's events name only
 * Variants.
 */
public interface ProductNames {

  /** The version held of the Product, locking it until the transaction ends. */
  Optional<Long> versionOf(String sku);

  /** Keeps the Product's name, and each Variant's axis values, at {@code version}. */
  void save(String sku, long version, String name, Map<String, String> variantLabels);

  /**
   * Each of the Variants' names that is known: its Product's name, then its axis values in
   * brackets, such as {@code Google Pixel 9 (128 GB)}.
   */
  Map<String, String> namesOf(Collection<String> variantIds);
}
