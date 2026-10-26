package com.ecomm.recommendations.application.port.out;

import com.ecomm.recommendations.domain.Category;
import java.util.Optional;

public interface CategoryStore {

  /** The version held of the Category, if any. */
  Optional<Long> versionOf(String slug);

  /** Replaces whatever is held of the Category. */
  void save(Category category, long version, boolean removed);
}
