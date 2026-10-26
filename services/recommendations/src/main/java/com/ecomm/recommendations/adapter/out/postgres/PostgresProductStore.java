package com.ecomm.recommendations.adapter.out.postgres;

import com.ecomm.recommendations.application.port.out.ProductStore;
import com.ecomm.recommendations.domain.Product;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Products as rows of {@code products}, with every Variant they have had in {@code variants}, those
 * they have now marked {@code current}.
 */
@Component
class PostgresProductStore implements ProductStore {

  private final JdbcClient jdbc;

  PostgresProductStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Locks the row, if there is one, until the transaction ends. */
  @Override
  public Optional<Held> held(String sku) {
    return jdbc.sql(
            "SELECT version, category IS NULL AS without_category FROM products WHERE sku = :sku FOR UPDATE")
        .param("sku", sku)
        .query((rs, row) -> new Held(rs.getLong("version"), rs.getBoolean("without_category")))
        .optional();
  }

  @Override
  public boolean anyWithoutCategory() {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM products WHERE category IS NULL)")
        .query(Boolean.class)
        .single();
  }

  @Override
  public void save(Product product) {
    jdbc.sql(
            """
            INSERT INTO products (sku, version, removed, category)
            VALUES (:sku, :version, :removed, :category)
            ON CONFLICT (sku) DO UPDATE
              SET version = excluded.version, removed = excluded.removed,
                  category = excluded.category
            """)
        .param("sku", product.sku())
        .param("version", product.version())
        .param("removed", product.removed())
        .param("category", product.category())
        .update();
    jdbc.sql("UPDATE variants SET current = false WHERE sku = :sku")
        .param("sku", product.sku())
        .update();
    for (var variantId : product.variantIds()) {
      jdbc.sql(
              """
              INSERT INTO variants (variant_id, sku, current) VALUES (:variantId, :sku, true)
              ON CONFLICT (variant_id) DO UPDATE SET sku = excluded.sku, current = true
              """)
          .param("variantId", variantId)
          .param("sku", product.sku())
          .update();
    }
  }
}
