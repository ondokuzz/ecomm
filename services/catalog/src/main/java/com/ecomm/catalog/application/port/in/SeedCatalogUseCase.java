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

  /**
   * Gives each of {@code products} that is stored as a Catalog before Warranty terms stored it the
   * Warranty terms and Serial tracking it has here, changing nothing else of it, and publishes it;
   * returns how many it changed. A Product stored or saved since has both, even if it has no
   * warranty, so Staff's edits stay. Running it again changes nothing more.
   */
  int fillInWarrantyTerms(List<Product> products);
}
