package com.ecomm.catalog.application.port.out;

import com.ecomm.catalog.domain.Category;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

  Optional<Category> find(String slug);

  /** Ordered by slug. */
  List<Category> findAll();

  /** Stores a new Category; returns false, storing nothing, when the slug is already taken. */
  boolean insert(Category category);

  /** Replaces an existing Category; returns false, storing nothing, when there is none. */
  boolean replace(Category category);

  /** Returns false when there was no Category to remove. */
  boolean remove(String slug);
}
