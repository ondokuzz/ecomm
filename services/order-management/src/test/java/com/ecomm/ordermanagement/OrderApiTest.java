package com.ecomm.ordermanagement;

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
 * Base for HTTP-seam tests: the app runs against one Postgres container shared by every test class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class OrderApiTest {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("orders");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  /** Two Pixel 9s at 799.00 EUR and one pair of AirPods Pro at 149.50 EUR: 1747.50 EUR in all. */
  static final String TWO_LINE_ORDER =
      """
      {"lines": [
        {"variantId": "PHN-PIXEL-9", "quantity": 2,
         "unitPrice": {"amountMinor": 79900, "currency": "EUR"}},
        {"variantId": "AUD-AIRPODS-PRO-2", "quantity": 1,
         "unitPrice": {"amountMinor": 14950, "currency": "EUR"}}
      ]}
      """;

  @Autowired RestTestClient http;

  static String customerToken() {
    return FakeKeycloak.token("customer-42", "CUSTOMER");
  }

  RestTestClient.ResponseSpec place(String token, String body) {
    return http.post()
        .uri("/orders")
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  RestTestClient.ResponseSpec place(String body) {
    return place(customerToken(), body);
  }

  /** Places {@link #TWO_LINE_ORDER} as {@code token}'s Customer and returns it. */
  OrderView placed(String token) {
    return place(token, TWO_LINE_ORDER)
        .expectStatus()
        .isCreated()
        .expectBody(OrderView.class)
        .returnResult()
        .getResponseBody();
  }

  OrderView placed() {
    return placed(customerToken());
  }

  RestTestClient.ResponseSpec changeStatus(String token, String id, String body) {
    return http.patch()
        .uri("/orders/{id}/status", id)
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  RestTestClient.ResponseSpec changeStatus(String id, String status) {
    return changeStatus(customerToken(), id, "{\"status\": \"" + status + "\"}");
  }

  /** The parts of an Order a client reads, independent of the service's classes. */
  record OrderView(
      String id, String status, List<LineView> lines, AmountView total, String placedAt) {}

  record LineView(String variantId, int quantity, AmountView unitPrice) {}

  record AmountView(long amountMinor, String currency) {}
}
