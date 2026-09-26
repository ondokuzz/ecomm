package com.ecomm.catalog.application.port.in;

import com.ecomm.catalog.domain.CategorySummary;
import com.ecomm.catalog.domain.Product;
import java.util.List;
import java.util.Optional;

public interface BrowseCatalogUseCase {

  Optional<Product> product(String sku);

  /** Products ordered by name; every Product when {@code category} is empty. */
  List<Product> products(Optional<String> category);

  /** Categories ordered by name. */
  List<CategorySummary> categories();
}
