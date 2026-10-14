package com.ecomm.fulfillmentshipping;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * The service refuses to start, listing the Shipments, when a Shipment names a Warehouse its
 * configuration no longer has.
 */
class UnconfiguredWarehouseApiTest extends ShipmentsApiTest {

  @Test
  void aShipmentFromAWarehouseNoLongerConfiguredStopsTheServiceStarting() {
    var amsterdam = created("NL", Map.of(CASE, 1));

    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(FulfillmentShippingApplication.class)
                    .run(
                        withInfrastructure(
                            // Amsterdam is gone: Berlin alone, the default.
                            "--ecomm.fulfillment-shipping.warehouses[0].id=WH-BER",
                            "--ecomm.fulfillment-shipping.warehouses[0].name=Berlin",
                            "--ecomm.fulfillment-shipping.warehouses[0].address.country=DE",
                            "--ecomm.fulfillment-shipping.warehouses[0].is-default=true"))
                    .close())
        .hasMessageContaining("name a Warehouse that isn't configured")
        .hasMessageContaining(amsterdam.id() + " (WH-AMS)");
  }

  /** The arguments that start the service against this test's Postgres, Kafka and Keycloak. */
  private static String[] withInfrastructure(String... args) {
    var all = new ArrayList<String>();
    DynamicPropertyRegistry registry = (name, value) -> all.add("--" + name + "=" + value.get());
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("ecomm.fulfillment-shipping.carrier-webhooks.secret", () -> "secret");
    registry.add("server.port", () -> "0");
    registry.add("spring.kafka.listener.auto-startup", () -> "false");
    all.addAll(java.util.List.of(args));
    return all.toArray(String[]::new);
  }
}
