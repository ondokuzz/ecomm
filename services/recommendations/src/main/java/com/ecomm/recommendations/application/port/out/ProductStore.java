package com.ecomm.recommendations.application.port.out;

import com.ecomm.recommendations.domain.Product;
import java.util.Optional;

public interface ProductStore {

  /** The version held of the Product, if any. */
  Optional<Long> versionOf(String sku);

  /**
   * Replaces the Product's version and whether it was removed, and adds its Variants to those it
   * already had.
   */
  void save(Product product);
}
