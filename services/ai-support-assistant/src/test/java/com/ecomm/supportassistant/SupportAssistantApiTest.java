package com.ecomm.supportassistant;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import java.util.Arrays;
import java.util.regex.Pattern;
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
 * Base for HTTP-seam tests. A WireMock server stands in for each context the tools read (Order
 * Management, Fulfillment & Shipping, Payment, Returns & Warranty and Search & Discovery), and one
 * Redis container holds the Conversations. The API key is empty, so the mock provider answers. Each
 * test starts with no stubs and no Conversations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
abstract class SupportAssistantApiTest {

  static final String CUSTOMER_ID = "customer-42";

  static final WireMockServer ORDER_MANAGEMENT = new WireMockServer(wireMockConfig().dynamicPort());

  static final WireMockServer FULFILLMENT_SHIPPING =
      new WireMockServer(wireMockConfig().dynamicPort());

  static final WireMockServer PAYMENT = new WireMockServer(wireMockConfig().dynamicPort());

  static final WireMockServer RETURNS_WARRANTY = new WireMockServer(wireMockConfig().dynamicPort());

  static final WireMockServer SEARCH_DISCOVERY = new WireMockServer(wireMockConfig().dynamicPort());

  static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(6379);

  static {
    ORDER_MANAGEMENT.start();
    FULFILLMENT_SHIPPING.start();
    PAYMENT.start();
    RETURNS_WARRANTY.start();
    SEARCH_DISCOVERY.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    registry.add("ecomm.support-assistant.order-management-url", ORDER_MANAGEMENT::baseUrl);
    registry.add("ecomm.support-assistant.fulfillment-shipping-url", FULFILLMENT_SHIPPING::baseUrl);
    registry.add("ecomm.support-assistant.payment-url", PAYMENT::baseUrl);
    registry.add("ecomm.support-assistant.returns-warranty-url", RETURNS_WARRANTY::baseUrl);
    registry.add("ecomm.support-assistant.search-discovery-url", SEARCH_DISCOVERY::baseUrl);
    // Long enough for a cold WireMock's first answer; the timeout test delays its by 3 seconds.
    registry.add("ecomm.support-assistant.read-timeout", () -> "2s");
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired RestTestClient http;

  private String customerToken;

  @BeforeEach
  void resetInfrastructure() throws Exception {
    // Minted once, so a request a test verifies carries the very token it sent, whatever second
    // it was sent in.
    customerToken = FakeKeycloak.token(CUSTOMER_ID, "CUSTOMER");
    ORDER_MANAGEMENT.resetAll();
    FULFILLMENT_SHIPPING.resetAll();
    PAYMENT.resetAll();
    RETURNS_WARRANTY.resetAll();
    SEARCH_DISCOVERY.resetAll();
    REDIS.execInContainer("redis-cli", "FLUSHALL");
  }

  /** The token of {@code customer-42}, the Customer every test asks as. */
  String customerToken() {
    return customerToken;
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

  /** What {@code /actuator/prometheus} answers. */
  String scrape() {
    return http.get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  /** The value of the series {@code name} whose labels include {@code label}. */
  static double value(String scrape, String name, String label) {
    var matcher =
        Pattern.compile(
                "(?m)^"
                    + Pattern.quote(name)
                    + "\\{[^}]*"
                    + Pattern.quote(label)
                    + "[^}]*\\} (\\S+)$")
            .matcher(scrape);
    assertThat(matcher.find()).as(name + "{" + label + "} in the scrape").isTrue();
    return Double.parseDouble(matcher.group(1));
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

  /** Order Management answers the Customer's Orders with the Order reference so. */
  static void stubOrdersByReference(String reference, String... orders) {
    ORDER_MANAGEMENT.stubFor(
        get(urlPathEqualTo("/orders"))
            .withQueryParam("reference", equalTo(reference))
            .willReturn(
                okJson(
                    "{\"items\": [%s], \"page\": 0, \"size\": 5, \"total\": %d}"
                        .formatted(String.join(",", orders), orders.length))));
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

  // --- Fulfillment & Shipping ---

  /**
   * The Order's Shipment in {@code status}, as Fulfillment & Shipping answers it: booked with the
   * Carrier when {@code trackingNumber} isn't null, with a Tracking event for each of {@code
   * tracking}, a day apart from 4 November 2026.
   */
  static String shipment(String orderId, String status, String trackingNumber, String... tracking) {
    var events = new StringBuilder();
    for (var i = 0; i < tracking.length; i++) {
      events
          .append(i == 0 ? "" : ",")
          .append(
              """
              {"eventId": "evt-%d", "status": "%s", "occurredAt": "2026-11-0%dT09:00:00Z"}
              """
                  .formatted(i, tracking[i], 4 + i));
    }
    var booked = trackingNumber != null;
    return """
        {"id": "5a1e0000-0000-4000-8000-000000000001", "kind": "ORDER", "rmaId": null,
         "orderId": "%s", "customerId": "%s", "warehouseId": "WH-BER", "status": "%s",
         "lines": [{"variantId": "PHN-PIXEL-9-128-OBS", "productName": "Pixel 9", "quantity": 1,
                    "serialTracking": "IMEI", "serialNumbers": []}],
         "carrier": %s, "trackingNumber": %s, "trackingEvents": [%s],
         "deliveredAt": %s, "holdReason": null, "statusHistory": [],
         "createdAt": "2026-11-03T10:16:00Z"}
        """
        .formatted(
            orderId,
            CUSTOMER_ID,
            status,
            booked ? "\"Mock Carrier\"" : "null",
            booked ? "\"" + trackingNumber + "\"" : "null",
            events,
            status.equals("DELIVERED") ? "\"2026-11-06T09:00:00Z\"" : "null");
  }

  /** Fulfillment & Shipping answers the Order's Shipment so. */
  static void stubShipment(String orderId, ResponseDefinitionBuilder response) {
    FULFILLMENT_SHIPPING.stubFor(
        get(urlPathEqualTo("/shipments"))
            .withQueryParam("orderId", equalTo(orderId))
            .willReturn(response));
  }

  // --- Payment ---

  /**
   * A Payment transaction of 803.99 EUR, or {@code amountMinor}, as Payment answers it, on {@code
   * day} of November 2026.
   */
  static String transaction(String kind, String outcome, int day) {
    return transaction(kind, 80399, outcome, null, day, null);
  }

  static String transaction(
      String kind,
      long amountMinor,
      String outcome,
      String declineReason,
      int day,
      String reference) {
    return """
        {"kind": "%s", "amount": {"amountMinor": %d, "currency": "EUR"}, "outcome": "%s",
         "gatewayReference": "mock-1", "declineReason": %s, "gatewayEventId": null,
         "at": "2026-11-0%dT10:15:00Z", "backfilled": false, "reference": %s}
        """
        .formatted(kind, amountMinor, outcome, json(declineReason), day, json(reference));
  }

  /** One of the Order's Payments, with its Payment transactions oldest first. */
  static String payment(String id, String orderId, String status, String... transactions) {
    return """
        {"id": "%s", "orderId": "%s", "amount": {"amountMinor": 80399, "currency": "EUR"},
         "status": "%s", "declineReason": null, "gatewayReference": "mock-1",
         "transactions": [%s]}
        """
        .formatted(id, orderId, status, String.join(",", transactions));
  }

  /** Payment answers the Customer's Payments for the Order, newest first, so. */
  static void stubPayments(String orderId, String... payments) {
    PAYMENT.stubFor(
        get(urlPathEqualTo("/payments"))
            .withQueryParam("orderId", equalTo(orderId))
            .willReturn(okJson("[" + String.join(",", payments) + "]")));
  }

  /** A line of the Payment transaction history: the transaction, on a Payment of the Order. */
  static String historyLine(String transaction, String orderId, String paymentStatus) {
    var line = transaction.strip();
    return line.substring(0, line.length() - 1)
        + """
        , "paymentId": "pay-%s", "paymentStatus": "%s", "orderId": "%s",
          "orderReference": "#%s"}
        """
            .formatted(
                orderId.substring(0, 8),
                paymentStatus,
                orderId,
                orderId.substring(0, 8).toUpperCase(java.util.Locale.ROOT));
  }

  /** Payment answers the Customer's Payment transaction history, newest first, as these lines. */
  static void stubHistory(String... lines) {
    stubHistory(
        okJson(
            "{\"items\": [%s], \"page\": 0, \"size\": 20, \"total\": %d}"
                .formatted(String.join(",", lines), lines.length)));
  }

  static void stubHistory(ResponseDefinitionBuilder response) {
    PAYMENT.stubFor(get(urlPathEqualTo("/payments/transactions")).willReturn(response));
  }

  // --- Returns & Warranty ---

  static final String RMA_ID = "3f2a9c1b-0000-4000-8000-000000000011";
  static final String OTHER_RMA_ID = "a9b8c7d6-0000-4000-8000-000000000012";

  /**
   * An RMA of {@code kind} for one Pixel 9 on {@link #ORDER_ID}, requested on 7 November 2026, as
   * Returns & Warranty answers it, in the last status of {@code history}: each step a day later
   * than the one before, written {@code "STATUS"} or {@code "STATUS:note"}. Once approved it goes
   * back to {@code WH-BER} by 21 November 2026; inspected, its Inspection is accepted, or refused
   * with the note of a {@code CLOSED} step; refunded, it names its Refund's reference.
   */
  static String rma(String id, String kind, String... history) {
    var steps = new StringBuilder();
    var status = "REQUESTED";
    var approved = false;
    var inspected = false;
    String closedNote = null;
    for (var i = 0; i < history.length; i++) {
      var step = history[i].split(":", 2);
      status = step[0];
      approved |= status.equals("APPROVED");
      inspected |= status.equals("INSPECTED");
      if (status.equals("CLOSED") && step.length > 1) {
        closedNote = step[1];
      }
      steps
          .append(i == 0 ? "" : ",")
          .append(
              """
              {"status": "%s", "by": "staff-1", "at": "2026-11-%02dT10:00:00Z", "note": %s}
              """
                  .formatted(status, 7 + i, json(step.length > 1 ? step[1] : null)));
    }
    var reference = "RMA-" + id.substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    var outcome = closedNote == null ? "ACCEPTED" : "REFUSED";
    return """
        {"id": "%s", "reference": "%s", "orderId": "%s", "customerId": "%s",
         "variantId": "PHN-PIXEL-9-128-OBS", "productName": "Pixel 9", "quantity": 1,
         "serialNumbers": ["356938035643809"], "kind": "%s", "reason": "Too big for my hand",
         "window": {"kind": "%s", "startsAt": "2026-11-06T09:00:00Z",
                    "endsAt": "2026-12-06T09:00:00Z"},
         "refundAmount": {"amountMinor": 79900, "currency": "EUR"},
         "status": "%s", "requestedAt": "2026-11-07T10:00:00Z",
         "sendBackBy": %s, "warehouseId": %s, "inspection": %s, "resolution": %s,
         "history": [%s]}
        """
        .formatted(
            id,
            reference,
            ORDER_ID,
            CUSTOMER_ID,
            kind,
            kind.equals("RETURN") ? "RETURN_PERIOD" : "WARRANTY_WINDOW",
            status,
            approved ? "\"2026-11-21T23:59:59Z\"" : "null",
            approved ? "\"WH-BER\"" : "null",
            inspected
                ? "{\"outcome\": \"%s\", \"note\": %s}".formatted(outcome, json(closedNote))
                : "null",
            status.equals("REFUNDED")
                ? "{\"paymentTransactionReference\": \"%s\"}".formatted(reference)
                : "null",
            steps);
  }

  /** Returns & Warranty answers the Customer's RMAs, newest first, as a page of these. */
  static void stubRmas(String... rmas) {
    stubRmas(rmaPage(rmas));
  }

  static void stubRmas(ResponseDefinitionBuilder response) {
    RETURNS_WARRANTY.stubFor(get(urlPathEqualTo("/rmas")).willReturn(response));
  }

  /** Returns & Warranty answers the Customer's RMAs whose {@code param} is {@code value} so. */
  static void stubRmasWhere(String param, String value, String... rmas) {
    RETURNS_WARRANTY.stubFor(
        get(urlPathEqualTo("/rmas"))
            .withQueryParam(param, equalTo(value))
            .willReturn(rmaPage(rmas)));
  }

  private static ResponseDefinitionBuilder rmaPage(String... rmas) {
    return okJson(
        "{\"items\": [%s], \"page\": 0, \"size\": 10, \"total\": %d}"
            .formatted(String.join(",", rmas), rmas.length));
  }

  /** Returns & Warranty answers the RMA {@code id} so. */
  static void stubRma(String id, ResponseDefinitionBuilder response) {
    RETURNS_WARRANTY.stubFor(get(urlPathEqualTo("/rmas/" + id)).willReturn(response));
  }

  /**
   * One delivered line of an Order's eligibility, as Returns & Warranty answers it: its Return
   * Period ending at {@code returnPeriodEndsAt}, its Warranty Window at {@code
   * warrantyWindowEndsAt} (none when null), with {@code quantity} units still free to claim.
   */
  static String eligibleLine(
      String productName, String returnPeriodEndsAt, String warrantyWindowEndsAt, int quantity) {
    return """
        {"variantId": "PHN-PIXEL-9-128-OBS", "productName": "%s", "returnPeriodEndsAt": "%s",
         "warrantyWindowEndsAt": %s, "serialNumbers": [], "quantity": %d}
        """
        .formatted(productName, returnPeriodEndsAt, json(warrantyWindowEndsAt), quantity);
  }

  /** Returns & Warranty answers the Order's eligibility with these lines. */
  static void stubEligibility(String orderId, String... lines) {
    stubEligibility(
        orderId,
        okJson(
            "{\"orderId\": \"%s\", \"lines\": [%s]}".formatted(orderId, String.join(",", lines))));
  }

  static void stubEligibility(String orderId, ResponseDefinitionBuilder response) {
    RETURNS_WARRANTY.stubFor(
        get(urlPathEqualTo("/rmas/eligibility"))
            .withQueryParam("orderId", equalTo(orderId))
            .willReturn(response));
  }

  /** Fulfillment & Shipping answers {@code WH-BER}'s Return address. */
  static void stubReturnAddress() {
    FULFILLMENT_SHIPPING.stubFor(
        get(urlPathEqualTo("/warehouses/WH-BER"))
            .willReturn(
                okJson(
                    """
                    {"id": "WH-BER", "name": "Berlin",
                     "returnAddress": {"name": "Ecomm Returns Berlin", "street": "Lagerweg 3",
                                       "postcode": "12099", "city": "Berlin", "country": "DE"}}
                    """)));
  }

  // --- Search & Discovery ---

  /**
   * The seeded Google Pixel 9, as Search & Discovery answers it: in Obsidian or Porcelain, 128 GB
   * at 799.00 EUR, and in Obsidian, 256 GB at 899.00 EUR, out of Stock; with Google's 24-month
   * Warranty terms.
   */
  static final String PIXEL_9 =
      """
      {"sku": "PHN-PIXEL-9", "name": "Google Pixel 9",
       "category": {"slug": "phones", "name": "Phones"},
       "attributes": {"brand": "Google", "screen": "6.3 in"},
       "warranty": {"months": 24, "manufacturer": "Google"},
       "variants": [{"id": "PHN-PIXEL-9", "axisValues": {"color": "Obsidian", "storage": "128 GB"},
                     "price": {"amountMinor": 79900, "currency": "EUR"}, "inStock": true},
                    {"id": "PHN-PIXEL-9-OBSIDIAN-256",
                     "axisValues": {"color": "Obsidian", "storage": "256 GB"},
                     "price": {"amountMinor": 89900, "currency": "EUR"}, "inStock": false},
                    {"id": "PHN-PIXEL-9-PORCELAIN-128",
                     "axisValues": {"color": "Porcelain", "storage": "128 GB"},
                     "price": {"amountMinor": 79900, "currency": "EUR"}, "inStock": true}]}
      """;

  /** Search & Discovery answers the Product {@code sku} so. */
  static void stubProduct(String sku, ResponseDefinitionBuilder response) {
    SEARCH_DISCOVERY.stubFor(get(urlPathEqualTo("/products/" + sku)).willReturn(response));
  }

  /**
   * A Product's summary, as a search answers it: its lowest Price, {@code priceVaries} when its
   * Variants' differ, and whether any Variant is in Stock.
   */
  static String productSummary(
      String sku, String name, long priceFromMinor, boolean priceVaries, boolean inStock) {
    return """
        {"sku": "%s", "name": "%s", "image": null,
         "priceFrom": {"amountMinor": %d, "currency": "EUR"}, "priceVaries": %s,
         "inStock": %s, "attributes": {"brand": "Google"}}
        """
        .formatted(sku, name, priceFromMinor, priceVaries, inStock);
  }

  /** Search & Discovery answers a search for {@code q}, without Facets, with these summaries. */
  static void stubSearch(String q, String... products) {
    stubSearch(
        q,
        okJson(
            "{\"items\": [%s], \"page\": 0, \"size\": 5, \"total\": %d}"
                .formatted(String.join(",", products), products.length)));
  }

  static void stubSearch(String q, ResponseDefinitionBuilder response) {
    SEARCH_DISCOVERY.stubFor(
        get(urlPathEqualTo("/search"))
            .withQueryParam("q", equalTo(q))
            .withQueryParam("facets", equalTo("false"))
            .willReturn(response));
  }

  private static String json(String text) {
    return text == null ? "null" : jsonString(text);
  }
}
