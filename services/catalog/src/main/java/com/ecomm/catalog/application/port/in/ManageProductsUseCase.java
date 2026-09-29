package com.ecomm.catalog.application.port.in;

import com.ecomm.catalog.domain.Product;

/** Staff-only changes to the Catalog. */
public interface ManageProductsUseCase {

  /**
   * Throws {@code ProductAlreadyExistsException} when the SKU is taken, {@code
   * VariantIdTakenException} when another Product has one of its Variant IDs, and {@code
   * InvalidProductException} when the Product breaks its Category's definitions (or it has none).
   * Each Variant's axis values are stored in the order the Category defines its axes.
   */
  Product create(Product product);

  /**
   * Throws {@code ProductNotFoundException} when there is no Product with the SKU, and {@code
   * VariantIdTakenException} and {@code InvalidProductException} as {@link #create} does.
   */
  Product update(Product product);

  /** Throws {@code ProductNotFoundException} when there is no Product with the SKU. */
  void delete(String sku);
}
