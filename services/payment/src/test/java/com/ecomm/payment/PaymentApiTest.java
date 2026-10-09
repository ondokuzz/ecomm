package com.ecomm.payment;

import com.ecomm.commons.events.EventBackbone;
import com.ecomm.commons.security.FakeKeycloak;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for HTTP-seam tests: the app runs against one Postgres container shared by every test class,
 * and the shared Kafka and schema registry from {@link EventBackbone}, with the Payment topic
 * created and its schema registered as the stack does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class PaymentApiTest {

  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16").withDatabaseName("payment");

  /** The topic Payment events are published to. */
  static final String PAYMENTS_TOPIC = "payment.payment";

  static {
    POSTGRES.start();
    EventBackbone.createTopic(PAYMENTS_TOPIC);
    EventBackbone.registerSchemaOf(PAYMENTS_TOPIC);
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    EventBackbone.registerWith(registry);
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // Sprint 3 Payments, there before the ledger and events (see BackfillApiTest).
    registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/testdata");
  }

  @Autowired RestTestClient http;

  /**
   * The token of {@code customer-42}, the Customer every test authorizes for unless it names
   * another.
   */
  static String customerToken() {
    return FakeKeycloak.token("customer-42", "CUSTOMER");
  }

  /** Orchestration's own token, with which the checkout Saga authorizes and voids. */
  static String orchestrationToken() {
    return FakeKeycloak.token("orchestration", "ORCHESTRATION");
  }

  static String staffToken() {
    return FakeKeycloak.token("staff-1", "STAFF");
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

  /** Authorizes as the checkout Saga with a fresh idempotency key; the body names the Customer. */
  RestTestClient.ResponseSpec authorize(String body) {
    return authorizeWithKey(UUID.randomUUID().toString(), body);
  }

  /** Authorizes as the checkout Saga with {@code idempotencyKey}; the body names the Customer. */
  RestTestClient.ResponseSpec authorizeWithKey(String idempotencyKey, String body) {
    return http.post()
        .uri("/payments")
        .headers(h -> h.setBearerAuth(orchestrationToken()))
        .header("Idempotency-Key", idempotencyKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** An authorized Payment of {@code orderId} for {@code customer-42}. */
  PaymentView authorized(String orderId) {
    return authorize(orderId, "tok_approve")
        .expectStatus()
        .isCreated()
        .expectBody(PaymentView.class)
        .returnResult()
        .getResponseBody();
  }

  /** Voids the Payment as the checkout Saga, for {@code customerId}. */
  RestTestClient.ResponseSpec voidPayment(String id, String customerId) {
    return http.post()
        .uri("/payments/{id}/void", id)
        .headers(h -> h.setBearerAuth(orchestrationToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"customerId\": \"%s\"}".formatted(customerId))
        .exchange();
  }

  /** Every Payment for the Order, as Staff read them. */
  List<PaymentView> staffPayments(String orderId) {
    return http.get()
        .uri("/staff/payments?orderId={orderId}", orderId)
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<List<PaymentView>>() {})
        .returnResult()
        .getResponseBody();
  }

  /** The parts of a Payment a client reads, independent of the service's classes. */
  record PaymentView(
      String id,
      String orderId,
      AmountView amount,
      String status,
      String declineReason,
      String gatewayReference,
      List<TransactionView> transactions) {}

  record TransactionView(
      String kind,
      AmountView amount,
      String outcome,
      String gatewayReference,
      String declineReason,
      String gatewayEventId,
      String at,
      boolean backfilled) {}

  record AmountView(long amountMinor, String currency) {}
}
