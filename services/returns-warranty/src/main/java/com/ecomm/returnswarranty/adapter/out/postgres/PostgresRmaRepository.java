package com.ecomm.returnswarranty.adapter.out.postgres;

import com.ecomm.returnswarranty.application.port.in.Page;
import com.ecomm.returnswarranty.application.port.in.RmaFilter;
import com.ecomm.returnswarranty.application.port.out.RmaRepository;
import com.ecomm.returnswarranty.domain.Rma;
import com.ecomm.returnswarranty.domain.RmaDecision;
import com.ecomm.returnswarranty.domain.RmaKind;
import com.ecomm.returnswarranty.domain.RmaStatus;
import com.ecomm.returnswarranty.domain.WarrantyTerms;
import com.ecomm.returnswarranty.domain.Window;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** RMAs as rows of {@code rma}, and their decision histories as rows of {@code rma_decision}. */
@Component
class PostgresRmaRepository implements RmaRepository {

  private static final String COLUMNS =
      """
      id, version, customer_id, order_id, variant_id, product_name, quantity, serial_numbers, kind,
      reason, status, requested_at, window_kind, window_starts_at, window_ends_at, warranty_months,
      warranty_manufacturer
      """;

  private final JdbcClient jdbc;

  PostgresRmaRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Rma rma) {
    var window = rma.window();
    var warranty = window.warranty();
    jdbc.sql(
            "INSERT INTO rma ("
                + COLUMNS
                + """
                ) VALUES (:id, :version, :customerId, :orderId, :variantId, :productName,
                  :quantity, :serialNumbers, :kind, :reason, :status, :requestedAt, :windowKind,
                  :startsAt, :endsAt, :months, :manufacturer)
                """)
        .param("id", rma.id())
        .param("version", rma.version())
        .param("customerId", rma.customerId())
        .param("orderId", rma.orderId())
        .param("variantId", rma.variantId())
        .param("productName", rma.productName())
        .param("quantity", rma.quantity())
        .param("serialNumbers", rma.serialNumbers().toArray(String[]::new))
        .param("kind", rma.kind().name())
        .param("reason", rma.reason())
        .param("status", rma.status().name())
        .param("requestedAt", Timestamp.from(rma.requestedAt()))
        .param("windowKind", window.kind().name())
        .param("startsAt", Timestamp.from(window.startsAt()))
        .param("endsAt", Timestamp.from(window.endsAt()))
        .param("months", warranty == null ? null : warranty.months())
        .param("manufacturer", warranty == null ? null : warranty.manufacturer())
        .update();
    insertDecisions(rma, 0);
  }

  @Override
  public void update(Rma rma) {
    jdbc.sql("UPDATE rma SET status = :status, version = :version WHERE id = :id")
        .param("status", rma.status().name())
        .param("version", rma.version())
        .param("id", rma.id())
        .update();
    var kept =
        jdbc.sql("SELECT count(*) FROM rma_decision WHERE rma_id = :id")
            .param("id", rma.id())
            .query(Integer.class)
            .single();
    insertDecisions(rma, kept);
  }

  private void insertDecisions(Rma rma, int from) {
    for (var position = from; position < rma.decisions().size(); position++) {
      var decision = rma.decisions().get(position);
      jdbc.sql(
              """
              INSERT INTO rma_decision (rma_id, position, status, decided_by, decided_at, note)
              VALUES (:id, :position, :status, :decidedBy, :decidedAt, :note)
              """)
          .param("id", rma.id())
          .param("position", position)
          .param("status", decision.status().name())
          .param("decidedBy", decision.decidedBy())
          .param("decidedAt", Timestamp.from(decision.decidedAt()))
          .param("note", decision.note())
          .update();
    }
  }

  @Override
  public Optional<Rma> find(UUID id) {
    return withDecisions(
            jdbc.sql("SELECT " + COLUMNS + " FROM rma WHERE id = :id")
                .param("id", id)
                .query(PostgresRmaRepository::rma)
                .list())
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Rma> lock(UUID id) {
    return withDecisions(
            jdbc.sql("SELECT " + COLUMNS + " FROM rma WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(PostgresRmaRepository::rma)
                .list())
        .stream()
        .findFirst();
  }

  @Override
  public List<Rma> ofOrder(String orderId) {
    return withDecisions(
        jdbc.sql("SELECT " + COLUMNS + " FROM rma WHERE order_id = :orderId")
            .param("orderId", orderId)
            .query(PostgresRmaRepository::rma)
            .list());
  }

  @Override
  public Page<Rma> find(RmaFilter filter, int page, int size) {
    var where = new StringBuilder(" WHERE true");
    var params = new HashMap<String, Object>();
    if (filter.customerId() != null) {
      where.append(" AND customer_id = :customerId");
      params.put("customerId", filter.customerId());
    }
    if (filter.status() != null) {
      where.append(" AND status = :status");
      params.put("status", filter.status().name());
    }
    if (filter.orderId() != null) {
      where.append(" AND order_id = :orderId");
      params.put("orderId", filter.orderId());
    }
    var total =
        jdbc.sql("SELECT count(*) FROM rma" + where).params(params).query(Long.class).single();
    var items =
        withDecisions(
            jdbc.sql(
                    "SELECT "
                        + COLUMNS
                        + " FROM rma"
                        + where
                        + " ORDER BY requested_at, id LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(PostgresRmaRepository::rma)
                .list());
    return new Page<>(items, page, size, total);
  }

  /** The RMAs, each with its decision history in order. */
  private List<Rma> withDecisions(List<Rma> rmas) {
    if (rmas.isEmpty()) {
      return rmas;
    }
    var decisions = new HashMap<UUID, List<RmaDecision>>();
    jdbc.sql(
            """
            SELECT rma_id, status, decided_by, decided_at, note FROM rma_decision
            WHERE rma_id IN (:ids) ORDER BY rma_id, position
            """)
        .param("ids", rmas.stream().map(Rma::id).toList())
        .query(
            (rs, row) -> {
              decisions
                  .computeIfAbsent(rs.getObject("rma_id", UUID.class), id -> new ArrayList<>())
                  .add(
                      new RmaDecision(
                          RmaStatus.valueOf(rs.getString("status")),
                          rs.getString("decided_by"),
                          rs.getTimestamp("decided_at").toInstant(),
                          rs.getString("note")));
              return null;
            })
        .list();
    return rmas.stream().map(rma -> withDecisions(rma, decisions)).toList();
  }

  private static Rma withDecisions(Rma rma, Map<UUID, List<RmaDecision>> decisions) {
    return new Rma(
        rma.id(),
        rma.version(),
        rma.customerId(),
        rma.orderId(),
        rma.variantId(),
        rma.productName(),
        rma.quantity(),
        rma.serialNumbers(),
        rma.kind(),
        rma.reason(),
        rma.window(),
        rma.status(),
        rma.requestedAt(),
        decisions.getOrDefault(rma.id(), List.of()));
  }

  private static Rma rma(ResultSet rs, int row) throws SQLException {
    var months = rs.getObject("warranty_months", Integer.class);
    return new Rma(
        rs.getObject("id", UUID.class),
        rs.getLong("version"),
        rs.getString("customer_id"),
        rs.getString("order_id"),
        rs.getString("variant_id"),
        rs.getString("product_name"),
        rs.getInt("quantity"),
        List.of((String[]) rs.getArray("serial_numbers").getArray()),
        RmaKind.valueOf(rs.getString("kind")),
        rs.getString("reason"),
        new Window(
            Window.Kind.valueOf(rs.getString("window_kind")),
            rs.getTimestamp("window_starts_at").toInstant(),
            rs.getTimestamp("window_ends_at").toInstant(),
            months == null
                ? null
                : new WarrantyTerms(months, rs.getString("warranty_manufacturer"))),
        RmaStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("requested_at").toInstant(),
        List.of());
  }
}
