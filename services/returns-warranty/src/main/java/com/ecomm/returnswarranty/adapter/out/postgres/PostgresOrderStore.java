package com.ecomm.returnswarranty.adapter.out.postgres;

import com.ecomm.returnswarranty.application.port.out.OrderStore;
import com.ecomm.returnswarranty.domain.Order;
import com.ecomm.returnswarranty.domain.OrderLine;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The Order projection, as rows of {@code customer_order} and {@code customer_order_line}. */
@Component
class PostgresOrderStore implements OrderStore {

  private final JdbcClient jdbc;

  PostgresOrderStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Long> versionOf(String id) {
    return jdbc.sql("SELECT version FROM customer_order WHERE id = :id")
        .param("id", id)
        .query(Long.class)
        .optional();
  }

  @Override
  public void save(Order order) {
    jdbc.sql(
            """
            INSERT INTO customer_order (id, version, currency, discounts_minor, tax_minor)
            VALUES (:id, :version, :currency, :discounts, :tax)
            ON CONFLICT (id) DO UPDATE SET
              version = EXCLUDED.version, currency = EXCLUDED.currency,
              discounts_minor = EXCLUDED.discounts_minor, tax_minor = EXCLUDED.tax_minor
            """)
        .param("id", order.id())
        .param("version", order.version())
        .param("currency", order.currency())
        .param("discounts", order.discountsMinor())
        .param("tax", order.taxMinor())
        .update();
    jdbc.sql("DELETE FROM customer_order_line WHERE order_id = :id")
        .param("id", order.id())
        .update();
    for (var position = 0; position < order.lines().size(); position++) {
      var line = order.lines().get(position);
      jdbc.sql(
              """
              INSERT INTO customer_order_line
                (order_id, position, variant_id, quantity, unit_price_minor)
              VALUES (:id, :position, :variantId, :quantity, :unitPrice)
              """)
          .param("id", order.id())
          .param("position", position)
          .param("variantId", line.variantId())
          .param("quantity", line.quantity())
          .param("unitPrice", line.unitPriceMinor())
          .update();
    }
  }

  @Override
  public Optional<Order> find(String id) {
    var lines =
        jdbc.sql(
                """
                SELECT variant_id, quantity, unit_price_minor FROM customer_order_line
                WHERE order_id = :id ORDER BY position
                """)
            .param("id", id)
            .query(
                (rs, row) ->
                    new OrderLine(
                        rs.getString("variant_id"),
                        rs.getInt("quantity"),
                        rs.getLong("unit_price_minor")))
            .list();
    return jdbc.sql(
            """
            SELECT version, currency, discounts_minor, tax_minor FROM customer_order WHERE id = :id
            """)
        .param("id", id)
        .query(
            (rs, row) ->
                new Order(
                    id,
                    rs.getLong("version"),
                    rs.getString("currency"),
                    lines,
                    rs.getLong("discounts_minor"),
                    rs.getLong("tax_minor")))
        .optional();
  }
}
