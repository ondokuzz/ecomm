package com.ecomm.ordermanagement.adapter.out.postgres;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.ecomm.commons.money.Money;
import com.ecomm.ordermanagement.application.port.out.OrderRepository;
import com.ecomm.ordermanagement.domain.Discount;
import com.ecomm.ordermanagement.domain.Order;
import com.ecomm.ordermanagement.domain.OrderLine;
import com.ecomm.ordermanagement.domain.OrderStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orders as rows of {@code customer_order}, with their lines in {@code order_line} (see {@code
 * db/migration}).
 */
@Component
class PostgresOrderRepository implements OrderRepository {

  /**
   * An Order's own row, before its lines are attached. Its discount and tax take their currency
   * from the lines.
   */
  private record OrderRow(
      UUID id,
      String customerId,
      String couponCode,
      Long discountMinor,
      long taxMinor,
      OrderStatus status,
      Instant placedAt) {

    Order with(List<OrderLine> lines) {
      var currency = lines.get(0).unitPrice().currency();
      var discount =
          Optional.ofNullable(couponCode)
              .map(code -> new Discount(code, new Money(discountMinor, currency)));
      return new Order(
          id, customerId, lines, discount, new Money(taxMinor, currency), status, placedAt);
    }
  }

  private record LineRow(UUID orderId, OrderLine line) {}

  private static final RowMapper<OrderRow> ORDER =
      (rs, row) ->
          new OrderRow(
              rs.getObject("id", UUID.class),
              rs.getString("customer_id"),
              rs.getString("coupon_code"),
              rs.getObject("discount_minor", Long.class),
              rs.getLong("tax_minor"),
              OrderStatus.valueOf(rs.getString("status")),
              rs.getTimestamp("placed_at").toInstant());

  private static final RowMapper<LineRow> LINE =
      (rs, row) ->
          new LineRow(
              rs.getObject("order_id", UUID.class),
              new OrderLine(
                  rs.getString("variant_id"),
                  rs.getInt("quantity"),
                  Money.of(rs.getLong("unit_price_minor"), rs.getString("currency"))));

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
              (id, customer_id, coupon_code, discount_minor, tax_minor, status, placed_at)
            VALUES
              (:id, :customerId, :couponCode, :discountMinor, :taxMinor, :status, :placedAt)
            """)
        .param("id", order.id())
        .param("customerId", order.customerId())
        .param("couponCode", order.discount().map(Discount::couponCode).orElse(null))
        .param("discountMinor", order.discount().map(d -> d.amount().amountMinor()).orElse(null))
        .param("taxMinor", order.tax().amountMinor())
        .param("status", order.status().name())
        .param("placedAt", Timestamp.from(order.placedAt()))
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
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Order> find(UUID id) {
    return withLines(
            jdbc.sql(
                    """
                    SELECT id, customer_id, coupon_code, discount_minor, tax_minor, status, placed_at
                    FROM customer_order WHERE id = :id
                    """)
                .param("id", id)
                .query(ORDER)
                .list())
        .stream()
        .findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Order> findByCustomer(String customerId) {
    return withLines(
        jdbc.sql(
                """
                SELECT id, customer_id, coupon_code, discount_minor, tax_minor, status, placed_at
                FROM customer_order WHERE customer_id = :customerId
                ORDER BY placed_at DESC, id
                """)
            .param("customerId", customerId)
            .query(ORDER)
            .list());
  }

  @Override
  public boolean replaceStatus(UUID id, OrderStatus from, OrderStatus to) {
    return jdbc.sql("UPDATE customer_order SET status = :to WHERE id = :id AND status = :from")
            .param("id", id)
            .param("from", from.name())
            .param("to", to.name())
            .update()
        == 1;
  }

  /** The Orders with their lines, fetched in one query and kept in order. */
  private List<Order> withLines(List<OrderRow> orders) {
    if (orders.isEmpty()) {
      return List.of();
    }
    Map<UUID, List<OrderLine>> linesByOrder =
        jdbc
            .sql(
                """
                SELECT order_id, variant_id, quantity, unit_price_minor, currency
                FROM order_line WHERE order_id IN (:orderIds) ORDER BY order_id, position
                """)
            .param("orderIds", orders.stream().map(OrderRow::id).toList())
            .query(LINE)
            .list()
            .stream()
            .collect(groupingBy(LineRow::orderId, mapping(LineRow::line, toList())));
    return orders.stream().map(order -> order.with(linesByOrder.get(order.id()))).toList();
  }
}
