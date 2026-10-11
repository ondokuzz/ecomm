package com.ecomm.recommendations.adapter.out.postgres;

import com.ecomm.recommendations.application.port.out.OrderStore;
import com.ecomm.recommendations.domain.Order;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Orders as rows of {@code orders}, with their Variants in {@code order_variants}. */
@Component
class PostgresOrderStore implements OrderStore {

  private final JdbcClient jdbc;

  PostgresOrderStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Locks the row, if there is one, until the transaction ends. */
  @Override
  public Optional<Long> versionOf(String orderId) {
    return jdbc.sql("SELECT version FROM orders WHERE order_id = :id FOR UPDATE")
        .param("id", orderId)
        .query(Long.class)
        .optional();
  }

  @Override
  public void save(Order order) {
    jdbc.sql(
            """
            INSERT INTO orders (order_id, version, counts, placed_at)
            VALUES (:id, :version, :counts, :placedAt)
            ON CONFLICT (order_id) DO UPDATE
              SET version = excluded.version, counts = excluded.counts,
                  placed_at = excluded.placed_at
            """)
        .param("id", order.orderId())
        .param("version", order.version())
        .param("counts", order.counts())
        .param("placedAt", Timestamp.from(order.placedAt()))
        .update();
    jdbc.sql("DELETE FROM order_variants WHERE order_id = :id")
        .param("id", order.orderId())
        .update();
    for (var variantId : order.variantIds()) {
      jdbc.sql("INSERT INTO order_variants (order_id, variant_id) VALUES (:id, :variantId)")
          .param("id", order.orderId())
          .param("variantId", variantId)
          .update();
    }
  }
}
