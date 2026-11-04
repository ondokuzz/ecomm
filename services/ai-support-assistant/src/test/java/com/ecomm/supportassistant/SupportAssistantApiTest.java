package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.ecomm.commons.security.FakeKeycloak;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;

/**
 * Base for HTTP-seam tests. One WireMock server stands in for Order Management, and one Redis
 * container holds the Conversations. The API key is empty, so the mock provider answers. Each test
 * starts with no stubs and no Conversations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class SupportAssistantApiTest {

  static final String CUSTOMER_ID = "customer-42";

  static final WireMockServer ORDER_MANAGEMENT = new WireMockServer(wireMockConfig().dynamicPort());

  static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(6379);

  static {
    ORDER_MANAGEMENT.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    registry.add("ecomm.support-assistant.order-management-url", ORDER_MANAGEMENT::baseUrl);
    registry.add("ecomm.support-assistant.read-timeout", () -> "1s");
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired RestTestClient http;

  @BeforeEach
  void resetInfrastructure() throws Exception {
    ORDER_MANAGEMENT.resetAll();
    REDIS.execInContainer("redis-cli", "FLUSHALL");
  }

  /** The token of {@code customer-42}, the Customer every test asks as. */
  static String customerToken() {
    return FakeKeycloak.token(CUSTOMER_ID, "CUSTOMER");
  }

  RestTestClient.ResponseSpec start(String token) {
    return http.post().uri("/conversations").headers(h -> h.setBearerAuth(token)).exchange();
  }

  /** Starts a Conversation for {@code customer-42}, which must succeed: its ID. */
  String started() {
    return start(customerToken())
        .expectStatus()
        .isCreated()
        .expectBody(ConversationView.class)
        .returnResult()
        .getResponseBody()
        .id();
  }

  RestTestClient.ResponseSpec transcript(String token, String conversationId) {
    return http.get()
        .uri("/conversations/{id}", conversationId)
        .headers(h -> h.setBearerAuth(token))
        .exchange();
  }

  RestTestClient.ResponseSpec end(String token, String conversationId) {
    return http.delete()
        .uri("/conversations/{id}", conversationId)
        .headers(h -> h.setBearerAuth(token))
        .exchange();
  }

  RestTestClient.ResponseSpec send(String conversationId, String text) {
    return send(customerToken(), conversationId, text);
  }

  RestTestClient.ResponseSpec send(String token, String conversationId, String text) {
    return sendBody(token, conversationId, "{\"text\": %s}".formatted(jsonString(text)));
  }

  RestTestClient.ResponseSpec sendBody(String token, String conversationId, String body) {
    return http.post()
        .uri("/conversations/{id}/messages", conversationId)
        .headers(h -> h.setBearerAuth(token))
        .header("X-Correlation-Id", "test-correlation-1")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  static String jsonString(String text) {
    return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  /** The part of a Conversation the tests read by type; the rest they read by JSON path. */
  record ConversationView(String id) {}

  // --- Order Management ---

  static final String ORDER_ID = "7f1c2a3b-0000-4000-8000-000000000001";
  static final String OTHER_ORDER_ID = "e5d40c11-0000-4000-8000-000000000002";

  /**
   * An Order of {@code customer-42}'s in {@code status}, placed on 3 November 2026, as Order
   * Management answers it, with its Order Status history up to that status.
   */
  static String order(String id, String status, String... history) {
    var entries =
        Arrays.stream(history)
            .map(
                s ->
                    """
                    {"status": "%s", "at": "2026-11-03T10:15:00Z", "changedBy": "ORCHESTRATION",
                     "backfilled": false}
                    """
                        .formatted(s))
            .collect(Collectors.joining(","));
    return """
        {"id": "%s", "customerId": "%s", "status": "%s",
         "lines": [{"variantId": "PHN-PIXEL-9-128-OBS", "quantity": 1,
                    "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}],
         "subtotal": {"amountMinor": 79900, "currency": "EUR"}, "discounts": [],
         "tax": {"amountMinor": 0, "currency": "EUR"},
         "shippingAddress": {"recipientName": "Erika Mustermann", "line1": "Hauptstraße 5",
                             "postcode": "10115", "city": "Berlin", "country": "DE"},
         "shippingOption": {"id": "STANDARD", "carrier": "Mock Carrier",
                            "cost": {"amountMinor": 499, "currency": "EUR"}},
         "total": {"amountMinor": 80399, "currency": "EUR"},
         "placedAt": "2026-11-03T10:15:00Z",
         "statusHistory": [%s]}
        """
        .formatted(id, CUSTOMER_ID, status, entries);
  }

  /** Order Management answers the Customer's Orders, newest first, as a page of these. */
  static void stubOrders(String... orders) {
    stubOrders(
        okJson(
            "{\"items\": [%s], \"page\": 0, \"size\": 5, \"total\": %d}"
                .formatted(String.join(",", orders), orders.length)));
  }

  static void stubOrders(ResponseDefinitionBuilder response) {
    ORDER_MANAGEMENT.stubFor(get(urlPathEqualTo("/orders")).willReturn(response));
  }

  /** Order Management answers the Order {@code id} so. */
  static void stubOrder(String id, ResponseDefinitionBuilder response) {
    ORDER_MANAGEMENT.stubFor(get(urlPathEqualTo("/orders/" + id)).willReturn(response));
  }

  /** The Customer's latest Order is {@link #ORDER_ID}, paid. */
  static void stubLatestOrderPaid() {
    var paid = order(ORDER_ID, "PAID", "PLACED", "PAID");
    stubOrders(paid);
    stubOrder(ORDER_ID, okJson(paid));
  }
}
