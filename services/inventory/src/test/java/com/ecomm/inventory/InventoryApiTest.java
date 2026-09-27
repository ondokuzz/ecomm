package com.ecomm.inventory;

import com.ecomm.commons.security.FakeKeycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for HTTP-seam tests: the app runs against one Postgres container shared by every test class.
 * Besides the seed stock, a test-only migration adds {@code TEST-*} Variants; each test that
 * changes stock uses Variants of its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class InventoryApiTest {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("inventory");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/testdata");
  }

  @Autowired RestTestClient http;

  /** Checkout's own token: the only caller allowed to decrement stock. */
  static String checkoutToken() {
    return FakeKeycloak.token("checkout", "CHECKOUT");
  }

  long quantityOf(String variantId) {
    return http.get()
        .uri("/stock/{variantId}", variantId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(StockView.class)
        .returnResult()
        .getResponseBody()
        .quantity();
  }

  /** The parts of a stock level a client reads, independent of the service's classes. */
  record StockView(String variantId, long quantity) {}
}
