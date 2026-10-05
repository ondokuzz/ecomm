package com.ecomm.inventory.adapter.out.postgres;

import com.ecomm.inventory.application.port.out.ReservationRepository;
import com.ecomm.inventory.domain.Reservation;
import com.ecomm.inventory.domain.ReservationStatus;
import com.ecomm.inventory.domain.StockBatch;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A Reservation as a {@code reservation} row and one {@code reservation_item} row per Variant it
 * holds (see {@code db/migration}).
 */
@Component
class PostgresReservationRepository implements ReservationRepository {

  private static final String SELECT =
      """
      SELECT r.id, r.customer_id, r.status, r.expires_at, i.variant_id, i.quantity
      FROM reservation r JOIN reservation_item i ON i.reservation_id = r.id
      """;

  private final JdbcClient jdbc;

  PostgresReservationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Reservation reservation) {
    jdbc.sql(
            """
            INSERT INTO reservation (id, customer_id, status, expires_at)
            VALUES (:id, :customerId, :status, :expiresAt)
            """)
        .param("id", reservation.id())
        .param("customerId", reservation.customerId())
        .param("status", reservation.status().name())
        .param("expiresAt", Timestamp.from(reservation.expiresAt()))
        .update();
    for (var line : reservation.items().lines()) {
      jdbc.sql(
              """
              INSERT INTO reservation_item (reservation_id, variant_id, quantity)
              VALUES (:id, :variantId, :quantity)
              """)
          .param("id", reservation.id())
          .param("variantId", line.variantId())
          .param("quantity", line.quantity())
          .update();
    }
  }

  @Override
  public Optional<Reservation> find(UUID id) {
    return single(query(SELECT + "WHERE r.id = :id", Map.of("id", id)));
  }

  @Override
  public Optional<Reservation> lock(UUID id) {
    // Lock the Reservation's own row, then read it whole; its items never change.
    return jdbc.sql("SELECT id FROM reservation WHERE id = :id FOR UPDATE")
        .param("id", id)
        .query(UUID.class)
        .optional()
        .flatMap(this::find);
  }

  @Override
  public void updateStatus(UUID id, ReservationStatus status) {
    jdbc.sql("UPDATE reservation SET status = :status WHERE id = :id")
        .param("status", status.name())
        .param("id", id)
        .update();
  }

  @Override
  public List<Reservation> activeFor(Collection<String> variantIds) {
    if (variantIds.isEmpty()) {
      return List.of();
    }
    return query(
        SELECT
            + """
            WHERE r.status = 'ACTIVE'
              AND r.id IN (SELECT reservation_id FROM reservation_item WHERE variant_id IN (:ids))
            """,
        Map.of("ids", variantIds));
  }

  @Override
  public List<Reservation> expired(Instant now) {
    return query(
        SELECT + "WHERE r.status = 'ACTIVE' AND r.expires_at <= :now",
        Map.of("now", Timestamp.from(now)));
  }

  @Override
  public List<UUID> releaseExpired(Collection<UUID> ids, Instant now) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jdbc.sql(
            """
            UPDATE reservation SET status = 'RELEASED'
            WHERE id IN (
              SELECT id FROM reservation
              WHERE id IN (:ids) AND status = 'ACTIVE' AND expires_at <= :now
              ORDER BY id
              FOR UPDATE SKIP LOCKED)
            RETURNING id
            """)
        .param("ids", ids)
        .param("now", Timestamp.from(now))
        .query(UUID.class)
        .list();
  }

  /** One Reservation per ID, from rows of it joined with each of its items. */
  private List<Reservation> query(String sql, Map<String, ?> params) {
    var rowsById = new LinkedHashMap<UUID, List<Row>>();
    jdbc.sql(sql)
        .params(params)
        .query(
            rs -> {
              var row =
                  new Row(
                      rs.getObject("id", UUID.class),
                      rs.getString("customer_id"),
                      ReservationStatus.valueOf(rs.getString("status")),
                      rs.getTimestamp("expires_at").toInstant(),
                      new StockBatch.Line(rs.getString("variant_id"), rs.getInt("quantity")));
              rowsById.computeIfAbsent(row.id(), id -> new ArrayList<>()).add(row);
            });
    return rowsById.values().stream()
        .map(
            rows -> {
              var first = rows.getFirst();
              return new Reservation(
                  first.id(),
                  first.customerId(),
                  StockBatch.of(rows.stream().map(Row::item).toList()),
                  first.status(),
                  first.expiresAt());
            })
        .toList();
  }

  private static Optional<Reservation> single(List<Reservation> reservations) {
    return reservations.stream().findFirst();
  }

  private record Row(
      UUID id,
      String customerId,
      ReservationStatus status,
      Instant expiresAt,
      StockBatch.Line item) {}
}
