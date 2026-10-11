package com.ecomm.recommendations.adapter.out.postgres;

import com.ecomm.recommendations.application.port.out.BoughtTogether;
import com.ecomm.recommendations.domain.Recommendation;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Joins the projection's tables when asked: the counting Orders that bought any of the Product's
 * Variants, then the other Products those Orders bought, each counted once per Order.
 */
@Component
class PostgresBoughtTogether implements BoughtTogether {

  private final JdbcClient jdbc;

  PostgresBoughtTogether(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Recommendation> with(String sku, int limit) {
    return jdbc.sql(
            """
            WITH bought_it AS (
              SELECT DISTINCT o.order_id, o.placed_at
              FROM variants v
              JOIN order_variants ov ON ov.variant_id = v.variant_id
              JOIN orders o ON o.order_id = ov.order_id
              WHERE v.sku = :sku AND o.counts
            )
            SELECT v.sku, COUNT(DISTINCT b.order_id) AS orders
            FROM bought_it b
            JOIN order_variants ov ON ov.order_id = b.order_id
            JOIN variants v ON v.variant_id = ov.variant_id
            JOIN products p ON p.sku = v.sku
            WHERE v.sku <> :sku AND NOT p.removed
            GROUP BY v.sku
            ORDER BY orders DESC, MAX(b.placed_at) DESC, v.sku
            LIMIT :limit
            """)
        .param("sku", sku)
        .param("limit", limit)
        .query((rs, row) -> new Recommendation(rs.getString("sku"), rs.getLong("orders")))
        .list();
  }
}
