package com.ecomm.inventory.adapter.out.postgres;

import com.ecomm.inventory.application.port.out.StockRepository;
import com.ecomm.inventory.domain.Stock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Stock as rows of the {@code stock} table, one per Variant (see {@code db/migration}). */
@Component
class PostgresStockRepository implements StockRepository {

  private static final RowMapper<Stock> STOCK =
      (rs, row) -> new Stock(rs.getString("variant_id"), rs.getInt("quantity"));

  private final JdbcClient jdbc;

  PostgresStockRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Stock> find(String variantId) {
    return jdbc.sql("SELECT variant_id, quantity FROM stock WHERE variant_id = :id")
        .param("id", variantId)
        .query(STOCK)
        .optional();
  }

  @Override
  public List<Stock> lockAll(Collection<String> variantIds) {
    return jdbc.sql(
            """
            SELECT variant_id, quantity FROM stock
            WHERE variant_id IN (:ids)
            ORDER BY variant_id
            FOR UPDATE
            """)
        .param("ids", variantIds)
        .query(STOCK)
        .list();
  }

  @Override
  public void updateAll(List<Stock> stock) {
    for (var s : stock) {
      jdbc.sql("UPDATE stock SET quantity = :quantity WHERE variant_id = :id")
          .param("quantity", s.quantity())
          .param("id", s.variantId())
          .update();
    }
  }
}
