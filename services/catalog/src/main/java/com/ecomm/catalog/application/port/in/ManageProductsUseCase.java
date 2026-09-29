package com.ecomm.catalog.application.port.in;

import com.ecomm.catalog.domain.Product;

/** Staff-only changes to the Catalog. */
public interface ManageProductsUseCase {

  /**
   * Throws {@code ProductAlreadyExistsException} when the SKU is taken, and {@code
   * InvalidProductException} when the Product breaks its Category's definitions (or it has none).
   */
  Product create(Product product);

  /**
   * Throws {@code ProductNotFoundException} when there is no Product with the SKU, and {@code
   * InvalidProductException} as {@link #create} does.
   */
  Product update(Product product);

  /** Throws {@code ProductNotFoundException} when there is no Product with the SKU. */
  void delete(String sku);
}
