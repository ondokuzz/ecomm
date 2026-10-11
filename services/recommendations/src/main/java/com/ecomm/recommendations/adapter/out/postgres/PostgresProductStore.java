package com.ecomm.recommendations.adapter.out.postgres;

import com.ecomm.recommendations.application.port.out.ProductStore;
import com.ecomm.recommendations.domain.Product;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Products as rows of {@code products}, with every Variant they have had in {@code variants}. */
@Component
class PostgresProductStore implements ProductStore {

  private final JdbcClient jdbc;

  PostgresProductStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Locks the row, if there is one, until the transaction ends. */
  @Override
  public Optional<Long> versionOf(String sku) {
    return jdbc.sql("SELECT version FROM products WHERE sku = :sku FOR UPDATE")
        .param("sku", sku)
        .query(Long.class)
        .optional();
  }

  @Override
  public void save(Product product) {
    jdbc.sql(
            """
            INSERT INTO products (sku, version, removed) VALUES (:sku, :version, :removed)
            ON CONFLICT (sku) DO UPDATE SET version = excluded.version, removed = excluded.removed
            """)
        .param("sku", product.sku())
        .param("version", product.version())
        .param("removed", product.removed())
        .update();
    for (var variantId : product.variantIds()) {
      jdbc.sql(
              """
              INSERT INTO variants (variant_id, sku) VALUES (:variantId, :sku)
              ON CONFLICT (variant_id) DO UPDATE SET sku = excluded.sku
              """)
          .param("variantId", variantId)
          .param("sku", product.sku())
          .update();
    }
  }
}
