package com.ecomm.catalog.application.port.out;

import com.ecomm.catalog.domain.Category;
import java.util.List;
import java.util.Optional;

/**
 * The Catalog's Categories. Writes, and {@link #nextVersion}, must run inside {@link
 * Transactions#inTransaction}.
 */
public interface CategoryRepository {

  Optional<Category> find(String slug);

  /** Ordered by slug. */
  List<Category> findAll();

  /** Stores a new Category; returns false, storing nothing, when the slug is already taken. */
  boolean insert(Category category);

  /** Replaces an existing Category; returns false, storing nothing, when there is none. */
  boolean replace(Category category);

  /** Removes the Category and returns it as it was; empty when there was none. */
  Optional<Category> remove(String slug);

  /**
   * Raises the version of the Category with slug {@code slug} and returns it: one more than the
   * last, or 1 when it has never had one. A slug keeps its version after its Category is removed.
   */
  long nextVersion(String slug);

  /** Whether the slug has a version; only a Category stored before Category events has none. */
  boolean hasVersion(String slug);
}
