package com.ecomm.notifications.application.port.out;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * The projection of Catalog's Products that names Order Lines and a Cart's lines, since an Order's
 * events and a Cart name only Variants. It also keeps each Variant's Price, and when it last
 * changed, for cart reminders.
 */
public interface ProductNames {

  /** A Variant's Price in a Product's event. */
  record VariantPrice(String label, Money price) {}

  /**
   * A Variant as projected: its Product's SKU, its name as {@link #namesOf} gives it, and when its
   * Price last changed, {@code null} when it hasn't since the projection first held one.
   */
  record ProjectedVariant(String sku, String name, Instant priceChangedAt) {}

  /** Whether any Variant was projected before Prices were kept, and so has none. */
  boolean anyWithoutPrice();

  /** Whether any of the Product's Variants has no Price, as one projected before Prices. */
  boolean withoutPrice(String sku);

  /** The version held of the Product, locking it until the transaction ends. */
  Optional<Long> versionOf(String sku);

  /**
   * Keeps the Product's name, and each Variant's axis values and Price, at {@code version}. A
   * Variant's Price that differs from the one held records {@code publishedAt} as when it changed.
   */
  void save(
      String sku,
      long version,
      String name,
      Map<String, VariantPrice> variants,
      Instant publishedAt);

  /**
   * Each of the Variants' names that is known: its Product's name, then its axis values in
   * brackets, such as {@code Google Pixel 9 (128 GB)}.
   */
  Map<String, String> namesOf(Collection<String> variantIds);

  /** Each of the Variants that is known. */
  Map<String, ProjectedVariant> variantsOf(Collection<String> variantIds);
}
