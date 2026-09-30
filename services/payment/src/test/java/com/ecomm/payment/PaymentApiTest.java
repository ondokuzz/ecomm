package com.ecomm.payment;

import com.ecomm.commons.security.FakeKeycloak;
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
abstract class PaymentApiTest {

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("payment");

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

  @Autowired RestTestClient http;

  /**
   * The token of {@code customer-42}, the Customer every test authorizes for unless it names
   * another.
   */
  static String customerToken() {
    return FakeKeycloak.token("customer-42", "CUSTOMER");
  }

  /** Checkout's own token: the only caller allowed to authorize a payment. */
  static String checkoutToken() {
    return FakeKeycloak.token("checkout", "CHECKOUT");
  }

  /**
   * Authorizes {@code amountMinor} EUR of {@code orderId} for {@code customer-42}, paid with {@code
   * paymentMethod}.
   */
  RestTestClient.ResponseSpec authorize(String orderId, String paymentMethod) {
    return authorize(
        """
        {"customerId": "customer-42", "orderId": "%s", "paymentMethod": "%s",
         "amount": {"amountMinor": 79900, "currency": "EUR"}}
        """
            .formatted(orderId, paymentMethod));
  }

  /** Authorizes as Checkout; the body names the Customer. */
  RestTestClient.ResponseSpec authorize(String body) {
    return http.post()
        .uri("/payments")
        .headers(h -> h.setBearerAuth(checkoutToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** The parts of a Payment a client reads, independent of the service's classes. */
  record PaymentView(
      String id,
      String orderId,
      AmountView amount,
      String status,
      String declineReason,
      String gatewayReference) {}

  record AmountView(long amountMinor, String currency) {}
}
