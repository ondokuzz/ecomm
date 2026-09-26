package com.ecomm.catalog.application.port.in;

import com.ecomm.catalog.domain.Product;
import java.util.List;

public interface SeedCatalogUseCase {

  /** Stores {@code products} only if the Catalog is empty; returns whether it did. */
  boolean seedIfEmpty(List<Product> products);
}
