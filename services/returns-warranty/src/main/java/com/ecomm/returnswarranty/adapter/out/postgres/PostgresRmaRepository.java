package com.ecomm.returnswarranty.adapter.out.postgres;

import com.ecomm.returnswarranty.application.port.in.Page;
import com.ecomm.returnswarranty.application.port.in.RmaFilter;
import com.ecomm.returnswarranty.application.port.out.RmaRepository;
import com.ecomm.returnswarranty.domain.Inspection;
import com.ecomm.returnswarranty.domain.InspectionOutcome;
import com.ecomm.returnswarranty.domain.Money;
import com.ecomm.returnswarranty.domain.Resolution;
import com.ecomm.returnswarranty.domain.Rma;
import com.ecomm.returnswarranty.domain.RmaHistoryEntry;
import com.ecomm.returnswarranty.domain.RmaKind;
import com.ecomm.returnswarranty.domain.RmaStatus;
import com.ecomm.returnswarranty.domain.WarrantyTerms;
import com.ecomm.returnswarranty.domain.Window;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** RMAs as rows of {@code rma}, and their RMA histories as rows of {@code rma_history}. */
@Component
class PostgresRmaRepository implements RmaRepository {

  private static final String COLUMNS =
      """
      id, version, customer_id, order_id, variant_id, product_name, quantity, serial_numbers, kind,
      reason, status, requested_at, window_kind, window_starts_at, window_ends_at, warranty_months,
      warranty_manufacturer, refund_amount_minor, refund_currency, send_back_by, warehouse_id,
      inspection_outcome, inspection_note, payment_transaction_reference, replacement_shipment_id
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
            """
            INSERT INTO rma (id, version, customer_id, order_id, variant_id, product_name,
              quantity, serial_numbers, kind, reason, status, requested_at, window_kind,
              window_starts_at, window_ends_at, warranty_months, warranty_manufacturer)
            VALUES (:id, :version, :customerId, :orderId, :variantId, :productName,
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
    update(rma);
  }

  @Override
  public void update(Rma rma) {
    var refund = rma.refundAmount();
    var inspection = rma.inspection();
    var resolution = rma.resolution();
    jdbc.sql(
            """
            UPDATE rma SET status = :status, version = :version,
              refund_amount_minor = :refundMinor, refund_currency = :refundCurrency,
              send_back_by = :sendBackBy, warehouse_id = :warehouseId,
              inspection_outcome = :outcome, inspection_note = :inspectionNote,
              payment_transaction_reference = :reference,
              replacement_shipment_id = :replacementShipmentId
            WHERE id = :id
            """)
        .param("status", rma.status().name())
        .param("version", rma.version())
        .param("refundMinor", refund == null ? null : refund.amountMinor())
        .param("refundCurrency", refund == null ? null : refund.currency())
        .param("sendBackBy", timestamp(rma.sendBackBy()))
        .param("warehouseId", rma.warehouseId())
        .param("outcome", inspection == null ? null : inspection.outcome().name())
        .param("inspectionNote", inspection == null ? null : inspection.note())
        .param("reference", resolution == null ? null : resolution.paymentTransactionReference())
        .param(
            "replacementShipmentId", resolution == null ? null : resolution.replacementShipmentId())
        .param("id", rma.id())
        .update();
    var kept =
        jdbc.sql("SELECT count(*) FROM rma_history WHERE rma_id = :id")
            .param("id", rma.id())
            .query(Integer.class)
            .single();
    for (var position = kept; position < rma.history().size(); position++) {
      var entry = rma.history().get(position);
      jdbc.sql(
              """
              INSERT INTO rma_history (rma_id, position, status, changed_by, changed_at, note)
              VALUES (:id, :position, :status, :by, :at, :note)
              """)
          .param("id", rma.id())
          .param("position", position)
          .param("status", entry.status().name())
          .param("by", entry.by())
          .param("at", Timestamp.from(entry.at()))
          .param("note", entry.note())
          .update();
    }
  }

  @Override
  public Optional<Rma> find(UUID id) {
    return withHistories(
            jdbc.sql("SELECT " + COLUMNS + " FROM rma WHERE id = :id")
                .param("id", id)
                .query(PostgresRmaRepository::rma)
                .list())
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Rma> lock(UUID id) {
    return withHistories(
            jdbc.sql("SELECT " + COLUMNS + " FROM rma WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(PostgresRmaRepository::rma)
                .list())
        .stream()
        .findFirst();
  }

  @Override
  public List<Rma> ofOrder(String orderId) {
    return withHistories(
        jdbc.sql(
                "SELECT "
                    + COLUMNS
                    + " FROM rma WHERE order_id = :orderId ORDER BY requested_at, id")
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
    if (!filter.statuses().isEmpty()) {
      where.append(" AND status IN (:statuses)");
      params.put("statuses", filter.statuses().stream().map(RmaStatus::name).toList());
    }
    if (filter.orderId() != null) {
      where.append(" AND order_id = :orderId");
      params.put("orderId", filter.orderId());
    }
    // Approved RMAs are chased by their Send-back deadline; every other list is oldest first.
    var order =
        filter.statuses().equals(Set.of(RmaStatus.APPROVED))
            ? " ORDER BY send_back_by, requested_at, id"
            : " ORDER BY requested_at, id";
    var total =
        jdbc.sql("SELECT count(*) FROM rma" + where).params(params).query(Long.class).single();
    var items =
        withHistories(
            jdbc.sql(
                    "SELECT "
                        + COLUMNS
                        + " FROM rma"
                        + where
                        + order
                        + " LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", size)
                .param("offset", (long) page * size)
                .query(PostgresRmaRepository::rma)
                .list());
    return new Page<>(items, page, size, total);
  }

  /** The RMAs, each with its RMA history in order. */
  private List<Rma> withHistories(List<Rma> rmas) {
    if (rmas.isEmpty()) {
      return rmas;
    }
    var histories = new HashMap<UUID, List<RmaHistoryEntry>>();
    jdbc.sql(
            """
            SELECT rma_id, status, changed_by, changed_at, note FROM rma_history
            WHERE rma_id IN (:ids) ORDER BY rma_id, position
            """)
        .param("ids", rmas.stream().map(Rma::id).toList())
        .query(
            (rs, row) -> {
              histories
                  .computeIfAbsent(rs.getObject("rma_id", UUID.class), id -> new ArrayList<>())
                  .add(
                      new RmaHistoryEntry(
                          RmaStatus.valueOf(rs.getString("status")),
                          rs.getString("changed_by"),
                          rs.getTimestamp("changed_at").toInstant(),
                          rs.getString("note")));
              return null;
            })
        .list();
    return rmas.stream().map(rma -> withHistory(rma, histories)).toList();
  }

  private static Rma withHistory(Rma rma, Map<UUID, List<RmaHistoryEntry>> histories) {
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
        rma.refundAmount(),
        rma.sendBackBy(),
        rma.warehouseId(),
        rma.inspection(),
        rma.resolution(),
        histories.getOrDefault(rma.id(), List.of()));
  }

  private static Rma rma(ResultSet rs, int row) throws SQLException {
    var months = rs.getObject("warranty_months", Integer.class);
    var refundMinor = rs.getObject("refund_amount_minor", Long.class);
    var outcome = rs.getString("inspection_outcome");
    var status = RmaStatus.valueOf(rs.getString("status"));
    var reference = rs.getString("payment_transaction_reference");
    var replacement = rs.getObject("replacement_shipment_id", UUID.class);
    var resolved =
        status == RmaStatus.REFUNDED || status == RmaStatus.EXCHANGED || status == RmaStatus.CLOSED;
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
        status,
        rs.getTimestamp("requested_at").toInstant(),
        refundMinor == null ? null : new Money(refundMinor, rs.getString("refund_currency")),
        instant(rs.getTimestamp("send_back_by")),
        rs.getString("warehouse_id"),
        outcome == null
            ? null
            : new Inspection(InspectionOutcome.valueOf(outcome), rs.getString("inspection_note")),
        resolved ? new Resolution(reference, replacement) : null,
        List.of());
  }

  private static Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private static Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
