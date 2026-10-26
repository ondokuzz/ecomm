package com.ecomm.recommendations.adapter.out.postgres;

import com.ecomm.recommendations.application.port.out.Candidates;
import com.ecomm.recommendations.domain.Candidate;
import com.ecomm.recommendations.domain.Category;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Joins the projection's tables when asked, counting each Order once per Product however many of
 * its Variants it bought.
 */
@Component
class PostgresCandidates implements Candidates {

  /** The Product {@code p} may be recommended: still in the Catalog, and in Stock. */
  private static final String RECOMMENDABLE =
      """
      NOT p.removed AND EXISTS (
        SELECT 1 FROM variants cv JOIN stock s ON s.variant_id = cv.variant_id
        WHERE cv.sku = p.sku AND cv.current AND s.in_stock
      )
      """;

  private final JdbcClient jdbc;

  PostgresCandidates(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Candidate> boughtWith(String sku, Instant since) {
    return jdbc.sql(
            """
            WITH bought_it AS (
              SELECT DISTINCT o.order_id, o.placed_at
              FROM variants v
              JOIN order_variants ov ON ov.variant_id = v.variant_id
              JOIN orders o ON o.order_id = ov.order_id
              WHERE v.sku = :sku AND o.counts AND o.placed_at >= :since
            )
            SELECT v.sku, COUNT(DISTINCT b.order_id) AS orders, MAX(b.placed_at) AS latest
            FROM bought_it b
            JOIN order_variants ov ON ov.order_id = b.order_id
            JOIN variants v ON v.variant_id = ov.variant_id
            JOIN products p ON p.sku = v.sku
            WHERE v.sku <> :sku AND %s
            GROUP BY v.sku
            """
                .formatted(RECOMMENDABLE))
        .param("sku", sku)
        .param("since", Timestamp.from(since))
        .query(PostgresCandidates::candidate)
        .list();
  }

  @Override
  public Optional<Category> categoryOf(String sku) {
    return jdbc.sql(
            """
            SELECT c.slug, c.name FROM products p JOIN categories c ON c.slug = p.category
            WHERE p.sku = :sku AND NOT c.removed
            """)
        .param("sku", sku)
        .query((rs, row) -> new Category(rs.getString("slug"), rs.getString("name")))
        .optional();
  }

  @Override
  public List<Candidate> bestSellersIn(String categorySlug, Instant since) {
    return jdbc.sql(
            """
            SELECT p.sku, COUNT(DISTINCT o.order_id) AS orders, MAX(o.placed_at) AS latest
            FROM products p
            JOIN variants v ON v.sku = p.sku
            JOIN order_variants ov ON ov.variant_id = v.variant_id
            JOIN orders o ON o.order_id = ov.order_id
            WHERE p.category = :category AND o.counts AND o.placed_at >= :since AND %s
            GROUP BY p.sku
            """
                .formatted(RECOMMENDABLE))
        .param("category", categorySlug)
        .param("since", Timestamp.from(since))
        .query(PostgresCandidates::candidate)
        .list();
  }

  private static Candidate candidate(ResultSet rs, int row) throws SQLException {
    return new Candidate(
        rs.getString("sku"), rs.getLong("orders"), rs.getTimestamp("latest").toInstant());
  }
}
