package com.ecomm.catalog.application.port.in;

import com.ecomm.catalog.domain.CategorySummary;
import com.ecomm.catalog.domain.Product;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

public interface BrowseCatalogUseCase {

  Optional<Product> product(String sku);

  /** The Product one of whose Variants has the ID {@code variantId}. */
  Optional<Product> productWithVariant(String variantId);

  /** Products ordered by name; every Product when {@code category} is empty. */
  List<Product> products(Optional<String> category);

  /** Every Category, with its Product count, ordered by slug. */
  List<CategorySummary> categories();

  /** The Category with its Product count; empty when there is none with the slug. */
  Optional<CategorySummary> category(String slug);

  /** Every currency a Price can be in, ordered by code. */
  List<Currency> currencies();
}
