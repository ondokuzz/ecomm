package com.ecomm.inventory;

import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for HTTP-seam tests: the app runs against one Postgres container shared by every test class.
 * Besides the seed stock, a test-only migration adds {@code TEST-*} Variants; each test that
 * changes stock uses Variants of its own, from that migration or from {@link #newVariant}.
 *
 * <p>The clock is a {@link TestTimeSource} and the scheduled sweeper is off, so a test sees what
 * happens before any sweep and triggers one itself.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "ecomm.inventory.reservation-sweeper.enabled=false")
@AutoConfigureRestTestClient
@Import(TestTimeSource.class)
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

  /** The Customer every test reserves for, unless it names another. */
  static final String CUSTOMER = "customer-42";

  @Autowired RestTestClient http;
  @Autowired TestTimeSource clock;

  /** Checkout's own token: the only caller allowed to decrement and reserve stock. */
  static String checkoutToken() {
    return FakeKeycloak.token("checkout", "CHECKOUT");
  }

  /** A Staff member's token: the only caller allowed to set on-hand Stock. */
  static String staffToken() {
    return FakeKeycloak.token("staff-1", "STAFF");
  }

  long quantityOf(String variantId) {
    return stockOf(variantId).quantity();
  }

  StockView stockOf(String variantId) {
    return http.get()
        .uri("/stock/{variantId}", variantId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(StockView.class)
        .returnResult()
        .getResponseBody();
  }

  RestTestClient.ResponseSpec setOnHand(String token, String variantId, String body) {
    return http.put()
        .uri("/stock/{variantId}", variantId)
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** A Variant no other test uses, with {@code onHand} units, set up by Staff. */
  String newVariant(int onHand) {
    var variantId = "TEST-" + UUID.randomUUID();
    setOnHand(staffToken(), variantId, onHandBody(onHand)).expectStatus().is2xxSuccessful();
    return variantId;
  }

  static String onHandBody(int onHand) {
    return """
        {"onHand": %d}
        """
        .formatted(onHand);
  }

  /** Fifteen minutes from the test clock's now, as a Checkout Session's hold lasts. */
  Instant inFifteenMinutes() {
    return clock.now().plus(Duration.ofMinutes(15));
  }

  RestTestClient.ResponseSpec reserve(String token, String body) {
    return http.post()
        .uri("/reservations")
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  RestTestClient.ResponseSpec reserve(String body) {
    return reserve(checkoutToken(), body);
  }

  /**
   * Reserves the items for {@link #CUSTOMER} until {@code expiresAt} and returns the Reservation.
   */
  ReservationView reserved(Instant expiresAt, Item... items) {
    return reserve(reservationBody(CUSTOMER, expiresAt, items))
        .expectStatus()
        .isCreated()
        .expectBody(ReservationView.class)
        .returnResult()
        .getResponseBody();
  }

  ReservationView reserved(Item... items) {
    return reserved(inFifteenMinutes(), items);
  }

  static String reservationBody(String customerId, Instant expiresAt, Item... items) {
    return """
        {"customerId": "%s", "expiresAt": "%s", "items": [%s]}
        """
        .formatted(
            customerId,
            expiresAt,
            Arrays.stream(items).map(Item::toJson).collect(Collectors.joining(", ")));
  }

  /** {@code action} is {@code commit} or {@code release}. */
  RestTestClient.ResponseSpec settle(String token, String id, String action, String customerId) {
    return http.post()
        .uri("/reservations/{id}/{action}", id, action)
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(customerBody(customerId))
        .exchange();
  }

  RestTestClient.ResponseSpec commit(String id) {
    return settle(checkoutToken(), id, "commit", CUSTOMER);
  }

  RestTestClient.ResponseSpec release(String id) {
    return settle(checkoutToken(), id, "release", CUSTOMER);
  }

  static String customerBody(String customerId) {
    return """
        {"customerId": "%s"}
        """
        .formatted(customerId);
  }

  static Item item(String variantId, int quantity) {
    return new Item(variantId, quantity);
  }

  record Item(String variantId, int quantity) {

    String toJson() {
      return """
          {"variantId": "%s", "quantity": %d}"""
          .formatted(variantId, quantity);
    }
  }

  /** The parts of a stock level a client reads, independent of the service's classes. */
  record StockView(String variantId, long quantity, long onHand, long reserved) {}

  record ReservationView(
      String id, String customerId, String status, String expiresAt, List<ItemView> items) {}

  record ItemView(String variantId, int quantity) {}
}
