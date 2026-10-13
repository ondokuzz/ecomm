package com.ecomm.fulfillmentshipping;

import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonCompareMode;

/** Staff read the configured Warehouses: Berlin, the default, and Amsterdam. */
class WarehouseApiTest extends FulfillmentShippingApiTest {

  @Test
  void staffSeeTheConfiguredWarehousesWithBerlinTheDefault() {
    http.get()
        .uri("/warehouses")
        .headers(h -> h.setBearerAuth(STAFF))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            [{"id": "WH-BER", "name": "Berlin",
              "address": {"line1": "Lagerstraße 1", "postcode": "10115", "city": "Berlin",
                          "country": "DE"},
              "default": true},
             {"id": "WH-AMS", "name": "Amsterdam",
              "address": {"line1": "Magazijnweg 1", "postcode": "1011 AB", "city": "Amsterdam",
                          "country": "NL"},
              "default": false}]
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void aCustomerMayNotListWarehouses() {
    http.get()
        .uri("/warehouses")
        .headers(h -> h.setBearerAuth(CUSTOMER))
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void checkoutMayNotListWarehouses() {
    http.get()
        .uri("/warehouses")
        .headers(h -> h.setBearerAuth(CHECKOUT))
        .exchange()
        .expectStatus()
        .isForbidden();
  }
}
