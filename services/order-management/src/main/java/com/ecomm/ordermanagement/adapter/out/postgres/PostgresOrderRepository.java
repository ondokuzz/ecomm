package com.ecomm.ordermanagement.adapter.out.postgres;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.application.port.in.OrderFilter;
import com.ecomm.ordermanagement.application.port.out.OrderRepository;
import com.ecomm.ordermanagement.domain.Caller;
import com.ecomm.ordermanagement.domain.Discount;
import com.ecomm.ordermanagement.domain.Order;
import com.ecomm.ordermanagement.domain.OrderLine;
import com.ecomm.ordermanagement.domain.OrderStatus;
import com.ecomm.ordermanagement.domain.StatusHistoryEntry;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orders as rows of {@code customer_order}, with their lines in {@code order_line}, their Discounts
 * in {@code order_discount} and their Order Status history in {@code order_status_history} (see
 * {@code db/migration}). The row's {@code status} is the current one, kept beside the history.
 */
@Component
class PostgresOrderRepository implements OrderRepository {

  private static final String ORDER_COLUMNS = "id, customer_id, tax_minor, version";

  /**
   * An Order's own row, before its lines, Discounts and history are attached. Its Discounts and tax
   * take their currency from the lines.
   */
  private record OrderRow(UUID id, String customerId, long taxMinor, long version) {

    Order with(
        List<OrderLine> lines, List<DiscountRow> discounts, List<StatusHistoryEntry> history) {
      var currency = lines.get(0).unitPrice().currency();
      return new Order(
          id,
          customerId,
          lines,
          discounts.stream().map(d -> d.toDiscount(currency)).toList(),
          new Money(taxMinor, currency),
          history,
          version);
    }
  }

  private record LineRow(UUID orderId, OrderLine line) {}

  private record DiscountRow(
      UUID orderId,
      String source,
      String couponCode,
      String campaignId,
      String campaignName,
      long amountMinor) {

    Discount toDiscount(Currency currency) {
      return new Discount(
          Discount.Source.valueOf(source),
          couponCode,
          campaignId,
          campaignName,
          new Money(amountMinor, currency));
    }
  }

  private record HistoryRow(UUID orderId, StatusHistoryEntry entry) {}

  private static final RowMapper<OrderRow> ORDER =
      (rs, row) ->
          new OrderRow(
              rs.getObject("id", UUID.class),
              rs.getString("customer_id"),
              rs.getLong("tax_minor"),
              rs.getLong("version"));

  private static final RowMapper<LineRow> LINE =
      (rs, row) ->
          new LineRow(
              rs.getObject("order_id", UUID.class),
              new OrderLine(
                  rs.getString("variant_id"),
                  rs.getInt("quantity"),
                  Money.of(rs.getLong("unit_price_minor"), rs.getString("currency"))));

  private static final RowMapper<DiscountRow> DISCOUNT =
      (rs, row) ->
          new DiscountRow(
              rs.getObject("order_id", UUID.class),
              rs.getString("source"),
              rs.getString("coupon_code"),
              rs.getString("campaign_id"),
              rs.getString("campaign_name"),
              rs.getLong("amount_minor"));

  private static final RowMapper<HistoryRow> HISTORY =
      (rs, row) ->
          new HistoryRow(
              rs.getObject("order_id", UUID.class),
              new StatusHistoryEntry(
                  OrderStatus.valueOf(rs.getString("status")),
                  rs.getTimestamp("at").toInstant(),
                  Caller.valueOf(rs.getString("changed_by")),
                  rs.getBoolean("backfilled")));

  private final JdbcClient jdbc;

  PostgresOrderRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void add(Order order) {
    jdbc.sql(
            """
            INSERT INTO customer_order
              (id, customer_id, tax_minor, status, placed_at, version)
            VALUES
              (:id, :customerId, :taxMinor, :status, :placedAt, :version)
            """)
        .param("id", order.id())
        .param("customerId", order.customerId())
        .param("taxMinor", order.tax().amountMinor())
        .param("status", order.status().name())
        .param("placedAt", Timestamp.from(order.placedAt()))
        .param("version", order.version())
        .update();
    var lines = order.lines();
    for (var position = 0; position < lines.size(); position++) {
      var line = lines.get(position);
      jdbc.sql(
              """
              INSERT INTO order_line
                (order_id, position, variant_id, quantity, unit_price_minor, currency)
              VALUES
                (:orderId, :position, :variantId, :quantity, :unitPriceMinor, :currency)
              """)
          .param("orderId", order.id())
          .param("position", position)
          .param("variantId", line.variantId())
          .param("quantity", line.quantity())
          .param("unitPriceMinor", line.unitPrice().amountMinor())
          .param("currency", line.unitPrice().currency().getCurrencyCode())
          .update();
    }
    var discounts = order.discounts();
    for (var position = 0; position < discounts.size(); position++) {
      var discount = discounts.get(position);
      jdbc.sql(
              """
              INSERT INTO order_discount
                (order_id, position, source, coupon_code, campaign_id, campaign_name, amount_minor)
              VALUES
                (:orderId, :position, :source, :couponCode, :campaignId, :campaignName,
                 :amountMinor)
              """)
          .param("orderId", order.id())
          .param("position", position)
          .param("source", discount.source().name())
          .param("couponCode", discount.couponCode())
          .param("campaignId", discount.campaignId())
          .param("campaignName", discount.campaignName())
          .param("amountMinor", discount.amount().amountMinor())
          .update();
    }
    for (var position = 0; position < order.statusHistory().size(); position++) {
      appendHistory(order, position);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Order> find(UUID id) {
    return attach(
            jdbc.sql("SELECT " + ORDER_COLUMNS + " FROM customer_order WHERE id = :id")
                .param("id", id)
                .query(ORDER)
                .list())
        .stream()
        .findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Order> findByCustomer(String customerId, long offset, int limit) {
    return attach(
        jdbc.sql(
                "SELECT "
                    + ORDER_COLUMNS
                    + """
                     FROM customer_order WHERE customer_id = :customerId
                    ORDER BY placed_at DESC, id LIMIT :limit OFFSET :offset
                    """)
            .param("customerId", customerId)
            .param("limit", limit)
            .param("offset", offset)
            .query(ORDER)
            .list());
  }

  @Override
  public long countByCustomer(String customerId) {
    return jdbc.sql("SELECT count(*) FROM customer_order WHERE customer_id = :customerId")
        .param("customerId", customerId)
        .query(Long.class)
        .single();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Order> findMatching(OrderFilter filter, long offset, int limit) {
    var where = Where.of(filter);
    return attach(
        where
            .bind(
                jdbc.sql(
                    "SELECT "
                        + ORDER_COLUMNS
                        + " FROM customer_order"
                        + where.sql()
                        + " ORDER BY placed_at DESC, id LIMIT :limit OFFSET :offset"))
            .param("limit", limit)
            .param("offset", offset)
            .query(ORDER)
            .list());
  }

  @Override
  public long countMatching(OrderFilter filter) {
    var where = Where.of(filter);
    return where
        .bind(jdbc.sql("SELECT count(*) FROM customer_order" + where.sql()))
        .query(Long.class)
        .single();
  }

  /** The {@code WHERE} clause a filter stands for, and the parameters it names. */
  private record Where(String sql, Map<String, Object> params) {

    static Where of(OrderFilter filter) {
      var conditions = new ArrayList<String>();
      var params = new HashMap<String, Object>();
      if (filter.customerId() != null) {
        conditions.add("customer_id = :customerId");
        params.put("customerId", filter.customerId());
      }
      if (filter.status() != null) {
        conditions.add("status = :status");
        params.put("status", filter.status().name());
      }
      if (filter.placedFrom() != null) {
        conditions.add("placed_at >= :placedFrom");
        params.put("placedFrom", Timestamp.from(filter.placedFrom()));
      }
      if (filter.placedTo() != null) {
        conditions.add("placed_at < :placedTo");
        params.put("placedTo", Timestamp.from(filter.placedTo()));
      }
      if (filter.idPrefix() != null) {
        // Hex digits and hyphens only, so nothing in it is a LIKE wildcard. No index serves it:
        // it reads every Order, which is fine while Staff look up one Order at a time.
        conditions.add("id::text LIKE :idPrefix");
        params.put("idPrefix", filter.idPrefix() + "%");
      }
      return new Where(
          conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions), params);
    }

    JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement) {
      return statement.params(params);
    }
  }

  @Override
  public boolean recordStatusChange(Order changed) {
    var updated =
        jdbc.sql(
                """
                UPDATE customer_order SET status = :status, version = :version
                WHERE id = :id AND version = :previousVersion
                """)
            .param("id", changed.id())
            .param("status", changed.status().name())
            .param("version", changed.version())
            .param("previousVersion", changed.version() - 1)
            .update();
    if (updated != 1) {
      return false;
    }
    appendHistory(changed, changed.statusHistory().size() - 1);
    return true;
  }

  @Override
  public List<Order> lockAwaitingBackfillEvent(int limit) {
    return attach(
        jdbc.sql(
                "SELECT "
                    + ORDER_COLUMNS
                    + """
                     FROM customer_order WHERE id IN (
                      SELECT order_id FROM order_awaiting_backfill_event
                      ORDER BY order_id LIMIT :limit FOR UPDATE SKIP LOCKED)
                    ORDER BY id
                    """)
            .param("limit", limit)
            .query(ORDER)
            .list());
  }

  @Override
  public void markBackfillPublished(List<UUID> ids) {
    if (!ids.isEmpty()) {
      jdbc.sql("DELETE FROM order_awaiting_backfill_event WHERE order_id IN (:ids)")
          .param("ids", ids)
          .update();
    }
  }

  private void appendHistory(Order order, int position) {
    var entry = order.statusHistory().get(position);
    jdbc.sql(
            """
            INSERT INTO order_status_history
              (order_id, position, status, at, changed_by, backfilled)
            VALUES (:orderId, :position, :status, :at, :changedBy, :backfilled)
            """)
        .param("orderId", order.id())
        .param("position", position)
        .param("status", entry.status().name())
        .param("at", Timestamp.from(entry.at()))
        .param("changedBy", entry.changedBy().name())
        .param("backfilled", entry.backfilled())
        .update();
  }

  /**
   * The Orders with their lines, Discounts and history, fetched in one query each and kept in
   * order.
   */
  private List<Order> attach(List<OrderRow> orders) {
    if (orders.isEmpty()) {
      return List.of();
    }
    var orderIds = orders.stream().map(OrderRow::id).toList();
    Map<UUID, List<OrderLine>> linesByOrder =
        jdbc
            .sql(
                """
                SELECT order_id, variant_id, quantity, unit_price_minor, currency
                FROM order_line WHERE order_id IN (:orderIds) ORDER BY order_id, position
                """)
            .param("orderIds", orderIds)
            .query(LINE)
            .list()
            .stream()
            .collect(groupingBy(LineRow::orderId, mapping(LineRow::line, toList())));
    Map<UUID, List<DiscountRow>> discountsByOrder =
        jdbc
            .sql(
                """
                SELECT order_id, source, coupon_code, campaign_id, campaign_name, amount_minor
                FROM order_discount WHERE order_id IN (:orderIds) ORDER BY order_id, position
                """)
            .param("orderIds", orderIds)
            .query(DISCOUNT)
            .list()
            .stream()
            .collect(groupingBy(DiscountRow::orderId));
    Map<UUID, List<StatusHistoryEntry>> historyByOrder =
        jdbc
            .sql(
                """
                SELECT order_id, status, at, changed_by, backfilled
                FROM order_status_history WHERE order_id IN (:orderIds)
                ORDER BY order_id, position
                """)
            .param("orderIds", orderIds)
            .query(HISTORY)
            .list()
            .stream()
            .collect(groupingBy(HistoryRow::orderId, mapping(HistoryRow::entry, toList())));
    return orders.stream()
        .map(
            order ->
                order.with(
                    linesByOrder.get(order.id()),
                    discountsByOrder.getOrDefault(order.id(), List.of()),
                    historyByOrder.get(order.id())))
        .toList();
  }
}
