package com.ecomm.catalog.application.port.in;

import com.ecomm.catalog.domain.Category;

/** Staff-only changes to the Catalog's Categories. */
public interface ManageCategoriesUseCase {

  /** Throws {@code CategoryAlreadyExistsException} when the slug is taken. */
  Category create(Category category);

  /**
   * Throws {@code CategoryNotFoundException} when there is no Category with the slug. Existing
   * Products are left as they are; the new definitions apply on each one's next write.
   */
  Category update(Category category);

  /**
   * Throws {@code CategoryNotFoundException} when there is no Category with the slug, and {@code
   * CategoryInUseException} while it still has Products.
   */
  void delete(String slug);
}
