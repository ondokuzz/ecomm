package com.ecomm.ordermanagement;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for HTTP-seam tests: the app runs against one Postgres container shared by every test class,
 * and the shared Kafka and schema registry from {@link EventBackbone}, with the Order topic created
 * and its schema registered as the stack does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class OrderApiTest {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("orders");

  /** The topic Order events are published to. */
  static final String ORDERS_TOPIC = "order-management.order";

  static {
    POSTGRES.start();
    EventBackbone.createTopic(ORDERS_TOPIC);
    EventBackbone.registerSchemaOf(ORDERS_TOPIC);
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // Sprint 2 Orders, there before histories and events (see BackfillApiTest).
    registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/testdata");
  }

  /** The Customer every test places Orders for, unless it names another. */
  static final String CUSTOMER = "customer-42";

  /**
   * Two Pixel 9s at 799.00 EUR and one pair of AirPods Pro at 149.50 EUR, with no discount and no
   * tax: 1747.50 EUR in all.
   */
  static String twoLineOrder(String customerId) {
    return """
        {"customerId": "%s", "lines": [
          {"variantId": "PHN-PIXEL-9", "quantity": 2,
           "unitPrice": {"amountMinor": 79900, "currency": "EUR"}},
          {"variantId": "AUD-AIRPODS-PRO-2", "quantity": 1,
           "unitPrice": {"amountMinor": 14950, "currency": "EUR"}}
        ],
         "tax": {"amountMinor": 0, "currency": "EUR"}}
        """
        .formatted(customerId);
  }

  @Autowired RestTestClient http;

  /** The Customer's own token, as the Storefront sends it. */
  static String tokenOf(String customerId) {
    return FakeKeycloak.token(customerId, "CUSTOMER");
  }

  static String customerToken() {
    return tokenOf(CUSTOMER);
  }

  /** Checkout's own token: the only caller allowed to place Orders and change their status. */
  static String checkoutToken() {
    return FakeKeycloak.token("checkout", "CHECKOUT");
  }

  /** Places an Order as Checkout; the body names the Customer. */
  RestTestClient.ResponseSpec place(String body) {
    return http.post()
        .uri("/orders")
        .headers(h -> h.setBearerAuth(checkoutToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** Places the two-line Order for {@code customerId} and returns it. */
  OrderView placed(String customerId) {
    return place(twoLineOrder(customerId))
        .expectStatus()
        .isCreated()
        .expectBody(OrderView.class)
        .returnResult()
        .getResponseBody();
  }

  OrderView placed() {
    return placed(CUSTOMER);
  }

  RestTestClient.ResponseSpec changeStatus(String token, String id, String body) {
    return http.patch()
        .uri("/orders/{id}/status", id)
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** Moves {@link #CUSTOMER}'s Order to {@code status} as Checkout. */
  RestTestClient.ResponseSpec changeStatus(String id, String status) {
    return changeStatus(checkoutToken(), id, statusChange(CUSTOMER, status));
  }

  static String statusChange(String customerId, String status) {
    return """
        {"customerId": "%s", "status": "%s"}
        """
        .formatted(customerId, status);
  }

  /** The parts of an Order a client reads, independent of the service's classes. */
  record OrderView(
      String id,
      String status,
      List<LineView> lines,
      AmountView subtotal,
      DiscountView discount,
      AmountView tax,
      AmountView total,
      String placedAt,
      List<HistoryEntryView> statusHistory) {}

  record HistoryEntryView(String status, String at, String changedBy, boolean backfilled) {}

  record LineView(String variantId, int quantity, AmountView unitPrice) {}

  record DiscountView(String couponCode, AmountView amount) {}

  record AmountView(long amountMinor, String currency) {}
}
