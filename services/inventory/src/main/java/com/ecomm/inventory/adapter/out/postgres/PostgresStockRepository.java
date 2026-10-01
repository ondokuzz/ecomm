package com.ecomm.inventory.adapter.out.postgres;

import com.ecomm.inventory.application.port.out.StockRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** On-hand Stock as rows of the {@code stock} table, one per Variant (see {@code db/migration}). */
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
  public boolean insertIfAbsent(String variantId, int onHand) {
    return jdbc.sql(
                """
                INSERT INTO stock (variant_id, on_hand) VALUES (:id, :onHand)
                ON CONFLICT (variant_id) DO NOTHING
                """)
            .param("id", variantId)
            .param("onHand", onHand)
            .update()
        == 1;
  }

  @Override
  public void setOnHand(Map<String, Integer> onHandByVariant) {
    onHandByVariant.forEach(
        (variantId, onHand) ->
            jdbc.sql("UPDATE stock SET on_hand = :onHand WHERE variant_id = :id")
                .param("onHand", onHand)
                .param("id", variantId)
                .update());
  }

  @Override
  public void delete(String variantId) {
    jdbc.sql("DELETE FROM stock WHERE variant_id = :id").param("id", variantId).update();
  }
}
