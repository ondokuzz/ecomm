package com.ecomm.catalog.application.port.out;

import com.ecomm.catalog.domain.CategorySummary;
import com.ecomm.catalog.domain.Product;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

  Optional<Product> find(String sku);

  /** Ordered by name. */
  List<Product> findAll();

  /** Ordered by name. */
  List<Product> findByCategory(String category);

  /** Ordered by category. */
  List<CategorySummary> categories();

  boolean isEmpty();

  /** Stores a new Product; returns false, storing nothing, when the SKU is already taken. */
  boolean insert(Product product);

  /** Replaces an existing Product; returns false, storing nothing, when there is none. */
  boolean replace(Product product);

  /** Returns false when there was no Product to remove. */
  boolean remove(String sku);
}
