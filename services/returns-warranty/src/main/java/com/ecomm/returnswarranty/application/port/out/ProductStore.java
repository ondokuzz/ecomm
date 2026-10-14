package com.ecomm.returnswarranty.application.port.out;

import com.ecomm.returnswarranty.domain.Product;
import java.util.Optional;

/** The Product projection. */
public interface ProductStore {

  /** The version of the Product held for {@code sku}, if any. */
  Optional<Long> versionOf(String sku);

  /** Keeps the Product, adding its Variants to those it had. */
  void save(Product product);

  /** The Product of {@code variantId}, if one is held. */
  Optional<Product> productOf(String variantId);
}
