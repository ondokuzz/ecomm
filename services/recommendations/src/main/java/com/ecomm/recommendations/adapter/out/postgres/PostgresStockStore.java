package com.ecomm.recommendations.adapter.out.postgres;

import com.ecomm.recommendations.application.port.out.StockStore;
import com.ecomm.recommendations.domain.VariantStock;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Each Variant's Stock as a row of {@code stock}: whether it is in Stock. */
@Component
class PostgresStockStore implements StockStore {

  private final JdbcClient jdbc;

  PostgresStockStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Locks the row, if there is one, until the transaction ends. */
  @Override
  public Optional<Long> versionOf(String variantId) {
    return jdbc.sql("SELECT version FROM stock WHERE variant_id = :id FOR UPDATE")
        .param("id", variantId)
        .query(Long.class)
        .optional();
  }

  @Override
  public void save(VariantStock stock) {
    jdbc.sql(
            """
            INSERT INTO stock (variant_id, version, in_stock) VALUES (:id, :version, :inStock)
            ON CONFLICT (variant_id) DO UPDATE
              SET version = excluded.version, in_stock = excluded.in_stock
            """)
        .param("id", stock.variantId())
        .param("version", stock.version())
        .param("inStock", stock.inStock())
        .update();
  }
}
