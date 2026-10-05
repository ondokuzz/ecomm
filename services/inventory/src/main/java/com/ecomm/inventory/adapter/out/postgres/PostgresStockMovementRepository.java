package com.ecomm.inventory.adapter.out.postgres;

import com.ecomm.inventory.application.port.out.StockMovementRepository;
import com.ecomm.inventory.domain.StockMovement;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The ledger as rows of the {@code stock_movement} table, ordered by their generated ID, which is
 * the order they were recorded in (see {@code db/migration}). Rows are only ever inserted.
 */
@Component
class PostgresStockMovementRepository implements StockMovementRepository {

  private static final RowMapper<StockMovement> MOVEMENT =
      (rs, row) ->
          new StockMovement(
              rs.getString("variant_id"),
              StockMovement.Kind.valueOf(rs.getString("kind")),
              rs.getInt("on_hand_change"),
              rs.getInt("reserved_change"),
              rs.getObject("reservation_id", UUID.class),
              rs.getString("reason"),
              rs.getTimestamp("at").toInstant());

  private final JdbcClient jdbc;

  PostgresStockMovementRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void append(Collection<StockMovement> movements) {
    for (var movement : movements) {
      jdbc.sql(
              """
              INSERT INTO stock_movement
                (variant_id, kind, on_hand_change, reserved_change, reservation_id, reason, at)
              VALUES
                (:variantId, :kind, :onHandChange, :reservedChange, :reservationId, :reason, :at)
              """)
          .param("variantId", movement.variantId())
          .param("kind", movement.kind().name())
          .param("onHandChange", movement.onHandChange())
          .param("reservedChange", movement.reservedChange())
          .param("reservationId", movement.reservationId())
          .param("reason", movement.reason())
          .param("at", Timestamp.from(movement.at()))
          .update();
    }
  }

  @Override
  public List<StockMovement> newestFirst(String variantId, long offset, int limit) {
    return jdbc.sql(
            """
            SELECT variant_id, kind, on_hand_change, reserved_change, reservation_id, reason, at
            FROM stock_movement WHERE variant_id = :id
            ORDER BY id DESC OFFSET :offset LIMIT :limit
            """)
        .param("id", variantId)
        .param("offset", offset)
        .param("limit", limit)
        .query(MOVEMENT)
        .list();
  }

  @Override
  public long count(String variantId) {
    return jdbc.sql("SELECT count(*) FROM stock_movement WHERE variant_id = :id")
        .param("id", variantId)
        .query(Long.class)
        .single();
  }

  @Override
  public long onHandSum(String variantId) {
    return jdbc.sql(
            "SELECT coalesce(sum(on_hand_change), 0) FROM stock_movement WHERE variant_id = :id")
        .param("id", variantId)
        .query(Long.class)
        .single();
  }
}
