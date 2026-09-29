package com.ecomm.catalog.application.port.in;

import com.ecomm.catalog.domain.Category;
import com.ecomm.catalog.domain.Product;
import java.util.List;

public interface SeedCatalogUseCase {

  /**
   * Stores {@code categories} and then {@code products} only if the Catalog is empty; returns
   * whether it did. Each Product must satisfy its Category's definitions, as on any write.
   */
  boolean seedIfEmpty(List<Category> categories, List<Product> products);
}
