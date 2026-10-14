package com.ecomm.fulfillmentshipping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Catalog's Products reach Shipments through the projection: the newest version's name and Serial
 * tracking, as each Shipment is created. A stale or repeated event changes nothing.
 */
class ProductProjectionApiTest extends ShipmentsApiTest {

  @Test
  void aShipmentNamesTheNewestVersionOfItsProduct() {
    var variant = "V-" + UUID.randomUUID();
    var product = Products.product("Galaxy S25", variant).tracked("IMEI").publish();
    product.named("Galaxy S25 Ultra").tracked("SERIAL").version(3).publish();
    product.named("Galaxy S25 (stale)").tracked("NONE").version(2).publish();
    var marker = "M-" + UUID.randomUUID();
    // The test topic has one partition, so once this later Product is known, so are the others.
    Products.product("Marker", marker).publish();
    created("DE", Map.of(marker, 1));

    var line = created("DE", Map.of(variant, 1)).lines().getFirst();

    assertThat(line.productName()).isEqualTo("Galaxy S25 Ultra");
    assertThat(line.serialTracking()).isEqualTo("SERIAL");
  }

  @Test
  void aProductWithoutSerialTrackingIsNotTracked() {
    var variant = "V-" + UUID.randomUUID();
    Products.product("Charger", variant).publish();

    assertThat(created("DE", Map.of(variant, 1)).lines().getFirst().serialTracking())
        .isEqualTo("NONE");
  }
}
