package com.ecomm.recommendations.application.port.out;

import com.ecomm.recommendations.domain.Product;
import java.util.Optional;

public interface ProductStore {

  /**
   * The Product held, if any: its version, and whether it was kept before Recommendations kept
   * Categories, and so has none.
   */
  record Held(long version, boolean withoutCategory) {

    /**
     * The version an event must be newer than to be applied: the one held, or, for a Product kept
     * without a Category, the one before it, so the same version is applied again.
     */
    public long versionToBeat() {
      return withoutCategory ? version - 1 : version;
    }
  }

  Optional<Held> held(String sku);

  /** Whether any Product held was kept before Recommendations kept Categories. */
  boolean anyWithoutCategory();

  /**
   * Replaces the Product's version, whether it was removed and its Category, and adds its Variants
   * to those it already had, marking them its current ones.
   */
  void save(Product product);
}
