package com.ecomm.catalog.application.port.out;

import com.ecomm.catalog.domain.Product;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Catalog's Products. Writes, and {@link #nextVersion}, must run inside {@link
 * Transactions#inTransaction}.
 */
public interface ProductRepository {

  Optional<Product> find(String sku);

  /** The Product one of whose Variants has the ID {@code variantId}. */
  Optional<Product> findByVariantId(String variantId);

  /** Which of {@code variantIds} belong to a Product other than the one with SKU {@code sku}. */
  List<String> variantIdsOfOtherProducts(String sku, List<String> variantIds);

  /** Ordered by name. */
  List<Product> findAll();

  /** Ordered by name. */
  List<Product> findByCategory(String category);

  /** How many Products each category slug has; a category with none is left out. */
  Map<String, Long> countByCategory();

  long countInCategory(String category);

  /** Whether the Catalog holds nothing at all: no Products and no Categories. */
  boolean isEmpty();

  /** Stores a new Product; returns false, storing nothing, when the SKU is already taken. */
  boolean insert(Product product);

  /** Replaces an existing Product; returns false, storing nothing, when there is none. */
  boolean replace(Product product);

  /** Removes the Product and returns it as it was; empty when there was none. */
  Optional<Product> remove(String sku);

  /**
   * Raises the version of the Product with SKU {@code sku} and returns it: one more than the last,
   * or 1 when it has never had one. A SKU keeps its version after its Product is removed, so a
   * Product created again with it carries on from there.
   */
  long nextVersion(String sku);

  /** Whether the SKU has a version; only a Product stored before Product events has none. */
  boolean hasVersion(String sku);
}
