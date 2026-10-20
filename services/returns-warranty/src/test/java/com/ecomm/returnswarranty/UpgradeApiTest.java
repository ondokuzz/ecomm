package com.ecomm.returnswarranty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ecomm.commons.events.EventBackbone;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * A Sprint 5 stack upgraded, its rows set up as the V5 migration leaves them: the projections fill
 * in what upgraded RMAs lack, their Warehouse from their Shipment and their Refund amount from
 * their Order, and publish each RMA they changed.
 */
class UpgradeApiTest extends RmasApiTest {

  private static final JsonMapper JSON = new JsonMapper();

  @Autowired JdbcClient jdbc;

  @Test
  void anUpgradedRmaGainsItsWarehouseAndRefundAmountOnceTheirEventsArrive() {
    var charger = newVariant("CHARGER");
    var shipment =
        Shipments.shipment(CUSTOMER_ID)
            .line(charger, "Charger", 1, "NONE")
            .withoutOrder()
            .delivered(RECENTLY);
    awaitShipmentsProjected();
    // As Sprint 5 left them: a Shipment kept without its Warehouse, and an approved RMA without its
    // Warehouse or Refund amount, its Send-back deadline set by the migration.
    var id = UUID.randomUUID();
    jdbc.sql("UPDATE shipment SET warehouse_id = NULL WHERE order_id = :orderId")
        .param("orderId", shipment.orderId())
        .update();
    Sprint5Rmas.insert(
        jdbc,
        id,
        "APPROVED",
        TestTimeSource.NOW.plus(Duration.ofDays(14)),
        shipment.orderId(),
        charger);
    jdbc.sql("UPDATE rma SET send_back_by = window_ends_at WHERE id = :id")
        .param("id", id)
        .update();

    // Replayed, the same version of the Shipment is applied again, now that it has a Warehouse.
    shipment.delivered(RECENTLY);
    Orders.order(shipment.orderId(), CUSTOMER_ID).line(charger, 1, 2499).publish();

    var upgraded =
        await()
            .atMost(Duration.ofSeconds(20))
            .until(
                () -> readRma(STAFF, id.toString()),
                rma -> rma.warehouseId() != null && rma.refundAmount() != null);
    assertThat(upgraded.warehouseId()).isEqualTo("WH-BER");
    assertThat(upgraded.refundAmount()).isEqualTo(new MoneyView(2499, "EUR"));
    assertThat(EventBackbone.recordsKeyed(RMA_TOPIC, id.toString()))
        .extracting(record -> JSON.readTree(record.value()).get("change").asString())
        .containsExactly("BACKFILLED", "BACKFILLED");
  }
}
