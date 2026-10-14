package com.ecomm.fulfillmentshipping.application.port.out;

import com.ecomm.fulfillmentshipping.domain.Product;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** The Product projection. */
public interface ProductStore {

  /** The version of the Product held for {@code sku}, if any. */
  Optional<Long> versionOf(String sku);

  /** Keeps the Product, adding its Variants to those it had. */
  void save(Product product);

  /** The Product of each of {@code variantIds} that has one, by Variant ID. */
  Map<String, Product> productsOf(Collection<String> variantIds);
}
