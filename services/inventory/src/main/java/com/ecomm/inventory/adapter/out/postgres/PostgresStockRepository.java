package com.ecomm.inventory.adapter.out.postgres;

import com.ecomm.inventory.application.port.out.StockRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * On-hand Stock as rows of the {@code stock} table, one per Variant, each with the version its last
 * write took from the {@code stock_version} sequence (see {@code db/migration}).
 */
@Component
class PostgresStockRepository implements StockRepository {

  private final JdbcClient jdbc;

  PostgresStockRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Integer> onHand(String variantId) {
    return jdbc.sql("SELECT on_hand FROM stock WHERE variant_id = :id")
        .param("id", variantId)
        .query(Integer.class)
        .optional();
  }

  @Override
  public Map<String, Integer> lockOnHand(Collection<String> variantIds) {
    var onHand = new LinkedHashMap<String, Integer>();
    jdbc.sql(
            """
            SELECT variant_id, on_hand FROM stock
            WHERE variant_id IN (:ids)
            ORDER BY variant_id
            FOR UPDATE
            """)
        .param("ids", variantIds)
        .query(
            rs -> {
              onHand.put(rs.getString("variant_id"), rs.getInt("on_hand"));
            });
    return onHand;
  }

  @Override
  public OptionalLong insertIfAbsent(String variantId, int onHand) {
    return jdbc.sql(
            """
            INSERT INTO stock (variant_id, on_hand, version)
            VALUES (:id, :onHand, nextval('stock_version'))
            ON CONFLICT (variant_id) DO NOTHING
            RETURNING version
            """)
        .param("id", variantId)
        .param("onHand", onHand)
        .query(Long.class)
        .optional()
        .map(OptionalLong::of)
        .orElseGet(OptionalLong::empty);
  }

  @Override
  public Map<String, Long> update(Map<String, Integer> onHandByVariant) {
    var versions = new TreeMap<String, Long>();
    onHandByVariant.forEach(
        (variantId, onHand) ->
            jdbc.sql(
                    """
                    UPDATE stock SET on_hand = :onHand, version = nextval('stock_version')
                    WHERE variant_id = :id
                    RETURNING version
                    """)
                .param("onHand", onHand)
                .param("id", variantId)
                .query(Long.class)
                .optional()
                .ifPresent(version -> versions.put(variantId, version)));
    return versions;
  }

  @Override
  public long delete(String variantId) {
    jdbc.sql("DELETE FROM stock WHERE variant_id = :id").param("id", variantId).update();
    return jdbc.sql("SELECT nextval('stock_version')").query(Long.class).single();
  }

  @Override
  public List<String> lockAwaitingBackfillEvent(int limit) {
    return jdbc.sql(
            """
            SELECT variant_id FROM stock_awaiting_backfill_event
            ORDER BY variant_id LIMIT :limit FOR UPDATE SKIP LOCKED
            """)
        .param("limit", limit)
        .query(String.class)
        .list();
  }

  @Override
  public void markBackfillPublished(Collection<String> variantIds) {
    if (!variantIds.isEmpty()) {
      jdbc.sql("DELETE FROM stock_awaiting_backfill_event WHERE variant_id IN (:ids)")
          .param("ids", variantIds)
          .update();
    }
  }
}
