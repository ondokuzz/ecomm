package com.ecomm.fulfillmentshipping;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonCompareMode;

/**
 * Staff read the configured Warehouses: Berlin, the default, and Amsterdam. Any signed-in Customer
 * reads one Warehouse's name and return address, to send an RMA's units back.
 */
class WarehouseApiTest extends FulfillmentShippingApiTest {

  static final String NOTIFICATIONS = FakeKeycloak.token("notifications", "NOTIFICATIONS");

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

  @Test
  void aCustomerStaffOrchestrationAndNotificationsReadAWarehousesNameAndReturnAddress() {
    for (var token : List.of(CUSTOMER, STAFF, ORCHESTRATION, NOTIFICATIONS)) {
      http.get()
          .uri("/warehouses/WH-AMS")
          .headers(h -> h.setBearerAuth(token))
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
              {"id": "WH-AMS", "name": "Amsterdam",
               "returnAddress": {"name": "Ecomm Returns Amsterdam", "street": "Magazijnweg 3",
                                 "postcode": "1011 AB", "city": "Amsterdam", "country": "NL"}}
              """,
              JsonCompareMode.STRICT);
    }
  }

  @Test
  void aWarehouseThatIsNotConfiguredIsNotFound() {
    http.get()
        .uri("/warehouses/WH-XXX")
        .headers(h -> h.setBearerAuth(CUSTOMER))
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void checkoutMayNotReadAWarehouse() {
    http.get()
        .uri("/warehouses/WH-BER")
        .headers(h -> h.setBearerAuth(CHECKOUT))
        .exchange()
        .expectStatus()
        .isForbidden();
  }
}
