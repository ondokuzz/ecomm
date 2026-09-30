package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.ecomm.commons.security.FakeKeycloak;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.containers.GenericContainer;

/**
 * Base for HTTP-seam tests. One WireMock server stands in for every service Checkout calls, and for
 * Keycloak's token endpoint; one Redis container holds the Checkout Sessions. Each test starts with
 * no stubs, no Checkout Sessions and no cached Checkout token, at the real time.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(CheckoutApiTest.Clocks.class)
abstract class CheckoutApiTest {

  static final String TOKEN_PATH = "/realms/ecomm/protocol/openid-connect/token";
  static final String CUSTOMER_ID = "customer-42";
  static final String ORDER_ID = "7f1c2a3b-0000-4000-8000-000000000001";
  static final String RESERVATION_ID = "5e2d1c0b-0000-4000-8000-000000000001";

  static final WireMockServer DOWNSTREAM = new WireMockServer(wireMockConfig().dynamicPort());

  static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(6379);

  static {
    DOWNSTREAM.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    FakeKeycloak.registerWith(registry);
    var url = DOWNSTREAM.baseUrl();
    registry.add("ecomm.checkout.cart-url", () -> url);
    registry.add("ecomm.checkout.catalog-url", () -> url);
    registry.add("ecomm.checkout.inventory-url", () -> url);
    registry.add("ecomm.checkout.order-management-url", () -> url);
    registry.add("ecomm.checkout.payment-url", () -> url);
    registry.add("ecomm.checkout.promotions-url", () -> url);
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.token-uri", () -> url + TOKEN_PATH);
    registry.add("spring.security.oauth2.client.registration.checkout.client-secret", () -> "test");
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  /**
   * A clock the tests move forward, to bring Checkout's cached token near its expiry or a Checkout
   * Session past its own.
   */
  @TestConfiguration
  static class Clocks {

    @Bean
    @Primary
    MovableClock movableClock() {
      return new MovableClock();
    }
  }

  @Autowired RestTestClient http;
  @Autowired MovableClock clock;
  @Autowired OAuth2AuthorizedClientService authorizedClients;

  @BeforeEach
  void resetDownstream() throws Exception {
    DOWNSTREAM.resetAll();
    REDIS.execInContainer("redis-cli", "FLUSHALL");
    authorizedClients.removeAuthorizedClient("checkout", "checkout");
    clock.reset();
  }

  /** The token of {@code customer-42}, the Customer every test checks out as. */
  static String customerToken() {
    return FakeKeycloak.token(CUSTOMER_ID, "CUSTOMER");
  }

  RestTestClient.ResponseSpec startSession() {
    return startSession(customerToken());
  }

  RestTestClient.ResponseSpec startSession(String token) {
    return http.post().uri("/checkout/sessions").headers(h -> h.setBearerAuth(token)).exchange();
  }

  /** Starts a Checkout Session that must succeed, and returns its ID. */
  String startedSessionId() {
    return startSession()
        .expectStatus()
        .isCreated()
        .expectBody(SessionView.class)
        .returnResult()
        .getResponseBody()
        .id();
  }

  RestTestClient.ResponseSpec currentSession() {
    return currentSession(customerToken());
  }

  RestTestClient.ResponseSpec currentSession(String token) {
    return http.get()
        .uri("/checkout/sessions/current")
        .headers(h -> h.setBearerAuth(token))
        .exchange();
  }

  /** The mock gateway's test token that approves. */
  static final String APPROVE = "tok_approve";

  RestTestClient.ResponseSpec pay(String sessionId) {
    return pay(customerToken(), sessionId);
  }

  RestTestClient.ResponseSpec pay(String token, String sessionId) {
    return payWith(token, sessionId, "{\"paymentMethod\": \"%s\"}".formatted(APPROVE));
  }

  /** Pays the session with {@code paymentMethod}, a gateway's test token. */
  RestTestClient.ResponseSpec payWithMethod(String sessionId, String paymentMethod) {
    return payWith(
        customerToken(), sessionId, "{\"paymentMethod\": \"%s\"}".formatted(paymentMethod));
  }

  /** Pays the session with the JSON {@code body} as it is. */
  RestTestClient.ResponseSpec payWith(String token, String sessionId, String body) {
    return http.post()
        .uri("/checkout/sessions/{id}/pay", sessionId)
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  /** Applies the Coupon {@code code} to the session, as its Customer. */
  RestTestClient.ResponseSpec applyCoupon(String sessionId, String code) {
    return applyCouponWith(customerToken(), sessionId, "{\"code\": \"%s\"}".formatted(code));
  }

  /** Applies a Coupon to the session with the JSON {@code body} as it is. */
  RestTestClient.ResponseSpec applyCouponWith(String token, String sessionId, String body) {
    return http.put()
        .uri("/checkout/sessions/{id}/coupon", sessionId)
        .headers(h -> h.setBearerAuth(token))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  RestTestClient.ResponseSpec removeCoupon(String sessionId) {
    return removeCoupon(customerToken(), sessionId);
  }

  RestTestClient.ResponseSpec removeCoupon(String token, String sessionId) {
    return http.delete()
        .uri("/checkout/sessions/{id}/coupon", sessionId)
        .headers(h -> h.setBearerAuth(token))
        .exchange();
  }

  /** Both steps of checkout: starts a Checkout Session, which must succeed, then pays it. */
  RestTestClient.ResponseSpec checkout() {
    return pay(startedSessionId());
  }

  /** The part of a Checkout Session the tests read by type; the rest they read by JSON path. */
  record SessionView(String id, Instant expiresAt) {}

  // --- Keycloak ---

  /** Keycloak hands out {@code token} for Checkout's client, valid for {@code expiresIn}. */
  static void stubServiceToken(String token, Duration expiresIn) {
    DOWNSTREAM.stubFor(
        post(TOKEN_PATH)
            .willReturn(
                okJson(
                    """
                    {"access_token": "%s", "token_type": "Bearer", "expires_in": %d}
                    """
                        .formatted(token, expiresIn.toSeconds()))));
  }

  static void stubServiceToken(String token) {
    stubServiceToken(token, Duration.ofMinutes(5));
  }

  // --- Cart ---

  /** The Customer's Cart, as Cart's JSON items: {@code {"variantId": …, "quantity": …}}. */
  static void stubCart(String itemsJson) {
    DOWNSTREAM.stubFor(get("/cart").willReturn(okJson("{\"items\": [" + itemsJson + "]}")));
  }

  static void stubClearCart() {
    DOWNSTREAM.stubFor(delete("/cart").willReturn(aResponse().withStatus(204)));
  }

  // --- Catalog ---

  /** A Variant of the Product {@code sku}, as Catalog's {@code /variants/{id}} serves it. */
  static void stubVariant(String variantId, String sku, long amountMinor) {
    DOWNSTREAM.stubFor(
        get("/variants/" + variantId)
            .willReturn(
                okJson(
                    """
                    {"id": "%s", "axisValues": {"color": "Obsidian"},
                     "price": {"amountMinor": %d, "currency": "EUR"}, "images": [],
                     "product": {"sku": "%s", "name": "%3$s", "images": []}}
                    """
                        .formatted(variantId, amountMinor, sku))));
  }

  /** A Product's first Variant, whose ID is its SKU. */
  static void stubVariant(String variantId, long amountMinor) {
    stubVariant(variantId, variantId, amountMinor);
  }

  // --- Inventory ---

  static void stubReserve() {
    stubReserve(RESERVATION_ID);
  }

  /** Inventory reserves the batch as the Reservation {@code reservationId}. */
  static void stubReserve(String reservationId) {
    stubReserve(
        aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(
                """
                {"id": "%s", "customerId": "%s", "status": "ACTIVE",
                 "expiresAt": "2030-01-01T00:00:00Z", "items": []}
                """
                    .formatted(reservationId, CUSTOMER_ID)));
  }

  static void stubReserve(ResponseDefinitionBuilder response) {
    DOWNSTREAM.stubFor(post("/reservations").willReturn(response));
  }

  static void stubCommit() {
    stubCommit(settledReservation("COMMITTED"));
  }

  static void stubCommit(ResponseDefinitionBuilder response) {
    DOWNSTREAM.stubFor(post(urlPathMatching("/reservations/[^/]+/commit")).willReturn(response));
  }

  static void stubRelease() {
    DOWNSTREAM.stubFor(
        post(urlPathMatching("/reservations/[^/]+/release"))
            .willReturn(settledReservation("RELEASED")));
  }

  private static ResponseDefinitionBuilder settledReservation(String status) {
    return okJson(
        """
        {"id": "%s", "customerId": "%s", "status": "%s",
         "expiresAt": "2030-01-01T00:00:00Z", "items": []}
        """
            .formatted(RESERVATION_ID, CUSTOMER_ID, status));
  }

  // --- Order Management ---

  /** Order Management places the Order, with the total a checkout of two Pixel 9s comes to. */
  static void stubPlaceOrder() {
    DOWNSTREAM.stubFor(post("/orders").willReturn(placedOrder()));
  }

  /** Order Management's answer to {@code POST /orders}: an Order for 1598.00 EUR. */
  static ResponseDefinitionBuilder placedOrder() {
    return placedOrder(159800);
  }

  /**
   * Order Management's answer to {@code POST /orders}: an Order whose total is {@code totalMinor}
   * EUR.
   */
  static ResponseDefinitionBuilder placedOrder(long totalMinor) {
    return aResponse()
        .withStatus(201)
        .withHeader("Content-Type", "application/json")
        .withBody(
            """
            {"id": "%s", "status": "PLACED", "total": {"amountMinor": %d, "currency": "EUR"}}
            """
                .formatted(ORDER_ID, totalMinor));
  }

  static void stubStatusChange() {
    DOWNSTREAM.stubFor(
        statusChange()
            .willReturn(
                okJson(
                    """
                    {"id": "%s", "status": "PAID"}
                    """
                        .formatted(ORDER_ID))));
  }

  static MappingBuilder statusChange() {
    return patch(urlPathMatching("/orders/[^/]+/status"));
  }

  // --- Payment ---

  static void stubPayment() {
    stubPayment(
        aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"id\": \"p-1\", \"status\": \"AUTHORIZED\"}"));
  }

  /** Payment records the Order's Payment as declined by the gateway, for {@code reason}. */
  static void stubDeclinedPayment(String reason) {
    stubPayment(
        aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(
                """
                {"id": "p-2", "status": "DECLINED", "declineReason": "%s"}
                """
                    .formatted(reason)));
  }

  static void stubPayment(ResponseDefinitionBuilder response) {
    DOWNSTREAM.stubFor(post("/payments").willReturn(response));
  }

  // --- Promotions ---

  static final String EVALUATE_PATH = "/discounts/evaluate";

  /** Promotions finds that the Coupon {@code couponCode} takes {@code discountMinor} EUR off. */
  static void stubDiscount(String couponCode, long discountMinor) {
    stubEvaluation(
        okJson(
            """
            {"couponCode": "%s", "discount": {"amountMinor": %d, "currency": "EUR"}}
            """
                .formatted(couponCode, discountMinor)));
  }

  /** Promotions finds the Coupon doesn't apply, for {@code reason}. */
  static void stubRejectedCoupon(String reason) {
    stubEvaluation(
        aResponse()
            .withStatus(422)
            .withHeader("Content-Type", "application/problem+json")
            .withBody(
                """
                {"type": "about:blank", "title": "Unprocessable Content", "status": 422,
                 "detail": "Coupon doesn't apply: %1$s", "reason": "%1$s"}
                """
                    .formatted(reason)));
  }

  static void stubEvaluation(ResponseDefinitionBuilder response) {
    DOWNSTREAM.stubFor(post(EVALUATE_PATH).willReturn(response));
  }

  /** Every downstream answers as a successful checkout of two Pixel 9s needs. */
  static void stubSuccessfulCheckout() {
    stubServiceToken("checkout-token-1");
    stubCart("{\"variantId\": \"PHN-PIXEL-9\", \"quantity\": 2}");
    stubVariant("PHN-PIXEL-9", 79900);
    stubReserve();
    stubRelease();
    stubPlaceOrder();
    stubPayment();
    stubCommit();
    stubStatusChange();
    stubClearCart();
  }

  /** A clock that reads the real time plus however far a test has moved it. */
  static final class MovableClock extends Clock {

    private volatile Duration offset = Duration.ZERO;

    void advance(Duration by) {
      offset = offset.plus(by);
    }

    void reset() {
      offset = Duration.ZERO;
    }

    @Override
    public Instant instant() {
      return Instant.now().plus(offset);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }
  }
}
