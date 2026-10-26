package com.ecomm.recommendations.adapter.out.postgres;

import com.ecomm.recommendations.application.port.out.CategoryStore;
import com.ecomm.recommendations.domain.Category;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Categories as rows of {@code categories}. */
@Component
class PostgresCategoryStore implements CategoryStore {

  private final JdbcClient jdbc;

  PostgresCategoryStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Locks the row, if there is one, until the transaction ends. */
  @Override
  public Optional<Long> versionOf(String slug) {
    return jdbc.sql("SELECT version FROM categories WHERE slug = :slug FOR UPDATE")
        .param("slug", slug)
        .query(Long.class)
        .optional();
  }

  @Override
  public void save(Category category, long version, boolean removed) {
    jdbc.sql(
            """
            INSERT INTO categories (slug, version, name, removed)
            VALUES (:slug, :version, :name, :removed)
            ON CONFLICT (slug) DO UPDATE
              SET version = excluded.version, name = excluded.name, removed = excluded.removed
            """)
        .param("slug", category.slug())
        .param("version", version)
        .param("name", category.name())
        .param("removed", removed)
        .update();
  }
}
