package com.ecomm.catalog.application.port.in;

import com.ecomm.catalog.domain.Product;

/** Staff-only changes to the Catalog. */
public interface ManageProductsUseCase {

  /** Throws {@code ProductAlreadyExistsException} when the SKU is taken. */
  Product create(Product product);

  /** Throws {@code ProductNotFoundException} when there is no Product with the SKU. */
  Product update(Product product);

  /** Throws {@code ProductNotFoundException} when there is no Product with the SKU. */
  void delete(String sku);
}
