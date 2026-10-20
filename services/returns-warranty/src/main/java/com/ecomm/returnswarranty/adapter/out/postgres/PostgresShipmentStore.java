package com.ecomm.returnswarranty.adapter.out.postgres;

import com.ecomm.returnswarranty.application.port.out.ShipmentStore;
import com.ecomm.returnswarranty.domain.DeliveredLine;
import com.ecomm.returnswarranty.domain.Delivery;
import com.ecomm.returnswarranty.domain.SerialTracking;
import com.ecomm.returnswarranty.domain.Shipment;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The Shipment projection, as rows of {@code shipment} and {@code shipment_line}. */
@Component
class PostgresShipmentStore implements ShipmentStore {

  private final JdbcClient jdbc;

  PostgresShipmentStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Long> versionOf(UUID id) {
    return jdbc.sql("SELECT version FROM shipment WHERE id = :id AND warehouse_id IS NOT NULL")
        .param("id", id)
        .query(Long.class)
        .optional();
  }

  @Override
  public void save(Shipment shipment) {
    jdbc.sql(
            """
            INSERT INTO shipment
              (id, version, order_id, customer_id, warehouse_id, status, delivered_at)
            VALUES (:id, :version, :orderId, :customerId, :warehouseId, :status, :deliveredAt)
            ON CONFLICT (id) DO UPDATE SET
              version = EXCLUDED.version, order_id = EXCLUDED.order_id,
              customer_id = EXCLUDED.customer_id, warehouse_id = EXCLUDED.warehouse_id,
              status = EXCLUDED.status, delivered_at = EXCLUDED.delivered_at
            """)
        .param("id", shipment.id())
        .param("version", shipment.version())
        .param("orderId", shipment.orderId())
        .param("customerId", shipment.customerId())
        .param("warehouseId", shipment.warehouseId())
        .param("status", shipment.status())
        .param(
            "deliveredAt",
            shipment.deliveredAt() == null ? null : Timestamp.from(shipment.deliveredAt()))
        .update();
    jdbc.sql("DELETE FROM shipment_line WHERE shipment_id = :id")
        .param("id", shipment.id())
        .update();
    for (var line : shipment.lines()) {
      jdbc.sql(
              """
              INSERT INTO shipment_line
                (shipment_id, variant_id, product_name, quantity, serial_tracking, serial_numbers)
              VALUES (:id, :variantId, :productName, :quantity, :serialTracking, :serialNumbers)
              """)
          .param("id", shipment.id())
          .param("variantId", line.variantId())
          .param("productName", line.productName())
          .param("quantity", line.quantity())
          .param("serialTracking", line.serialTracking().name())
          .param("serialNumbers", line.serialNumbers().toArray(String[]::new))
          .update();
    }
  }

  @Override
  public Optional<Delivery> lockDeliveryOf(String orderId) {
    return deliveryOf(orderId, " FOR UPDATE");
  }

  @Override
  public Optional<Delivery> deliveryOf(String orderId) {
    return deliveryOf(orderId, "");
  }

  @Override
  public boolean anyWithoutWarehouse() {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM shipment WHERE warehouse_id IS NULL)")
        .query(Boolean.class)
        .single();
  }

  private Optional<Delivery> deliveryOf(String orderId, String lock) {
    return jdbc.sql(
            """
            SELECT id, customer_id, warehouse_id, delivered_at FROM shipment
            WHERE order_id = :orderId AND status = 'DELIVERED' AND delivered_at IS NOT NULL
            """
                + lock)
        .param("orderId", orderId)
        .query(
            (rs, row) ->
                new Delivery(
                    rs.getObject("id", UUID.class),
                    orderId,
                    rs.getString("customer_id"),
                    rs.getString("warehouse_id"),
                    rs.getTimestamp("delivered_at").toInstant(),
                    List.of()))
        .optional()
        .map(delivery -> withLines(delivery));
  }

  private Delivery withLines(Delivery delivery) {
    var lines =
        jdbc.sql(
                """
                SELECT variant_id, product_name, quantity, serial_tracking, serial_numbers
                FROM shipment_line WHERE shipment_id = :id
                """)
            .param("id", delivery.shipmentId())
            .query(
                (rs, row) ->
                    new DeliveredLine(
                        rs.getString("variant_id"),
                        rs.getString("product_name"),
                        rs.getInt("quantity"),
                        SerialTracking.valueOf(rs.getString("serial_tracking")),
                        List.of((String[]) rs.getArray("serial_numbers").getArray())))
            .list();
    return new Delivery(
        delivery.shipmentId(),
        delivery.orderId(),
        delivery.customerId(),
        delivery.warehouseId(),
        delivery.deliveredAt(),
        lines);
  }
}
