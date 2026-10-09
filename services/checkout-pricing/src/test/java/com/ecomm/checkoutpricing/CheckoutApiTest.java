package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.ecomm.commons.security.FakeKeycloak;
import com.github.tomakehurst.wiremock.WireMockServer;
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
 * Keycloak's token endpoint; one Redis container holds the Checkout Sessions; {@link
 * FakeCheckoutSaga} stands in for the checkout Saga on Temporal. Each test starts with no stubs, no
 * Checkout Sessions, no Saga started and no cached Checkout token, at the real time.
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
    registry.add("ecomm.checkout.promotions-url", () -> url);
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.token-uri", () -> url + TOKEN_PATH);
    registry.add("spring.security.oauth2.client.registration.checkout.client-secret", () -> "test");
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  /**
   * A clock the tests move forward, to bring Checkout's cached token near its expiry or a Checkout
   * Session past its own, and the Saga the tests tell how each payment ends.
   */
  @TestConfiguration
  static class Clocks {

    @Bean
    @Primary
    MovableClock movableClock() {
      return new MovableClock();
    }

    @Bean
    @Primary
    FakeCheckoutSaga fakeCheckoutSaga() {
      return new FakeCheckoutSaga();
    }
  }

  @Autowired RestTestClient http;
  @Autowired MovableClock clock;
  @Autowired OAuth2AuthorizedClientService authorizedClients;
  @Autowired FakeCheckoutSaga saga;

  @BeforeEach
  void resetDownstream() throws Exception {
    DOWNSTREAM.resetAll();
    REDIS.execInContainer("redis-cli", "FLUSHALL");
    authorizedClients.removeAuthorizedClient("checkout", "checkout");
    clock.reset();
    saga.reset();
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

  /** The latest attempt to pay the session, as its Customer reads it. */
  RestTestClient.ResponseSpec payment(String sessionId) {
    return payment(customerToken(), sessionId);
  }

  RestTestClient.ResponseSpec payment(String token, String sessionId) {
    return http.get()
        .uri("/checkout/sessions/{id}/payment", sessionId)
        .headers(h -> h.setBearerAuth(token))
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

  // --- Catalog ---

  /**
   * A Variant of the Product {@code sku} in {@code category}, as Catalog's {@code /variants/{id}}
   * serves it.
   */
  static void stubVariant(String variantId, String sku, String category, long amountMinor) {
    DOWNSTREAM.stubFor(
        get("/variants/" + variantId)
            .willReturn(
                okJson(
                    """
                    {"id": "%s", "axisValues": {"color": "Obsidian"},
                     "price": {"amountMinor": %d, "currency": "EUR"}, "images": [],
                     "product": {"sku": "%s", "name": "%3$s", "category": "%s", "images": []}}
                    """
                        .formatted(variantId, amountMinor, sku, category))));
  }

  /** A phone Variant of the Product {@code sku}. */
  static void stubVariant(String variantId, String sku, long amountMinor) {
    stubVariant(variantId, sku, "phones", amountMinor);
  }

  /** A phone Product's first Variant, whose ID is its SKU. */
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

  // --- Promotions ---

  static final String EVALUATE_PATH = "/discounts/evaluate";

  /** A running Campaign's Discount of {@code amountMinor} EUR, as Promotions answers it. */
  static String campaignDiscount(String campaignId, String name, long amountMinor) {
    return """
        {"source": "CAMPAIGN", "campaignId": "%s", "campaignName": "%s",
         "amount": {"amountMinor": %d, "currency": "EUR"}}
        """
        .formatted(campaignId, name, amountMinor);
  }

  /** A Coupon's Discount of {@code amountMinor} EUR, as Promotions answers it. */
  static String couponDiscount(String couponCode, long amountMinor) {
    return """
        {"source": "COUPON", "couponCode": "%s", "amount": {"amountMinor": %d, "currency": "EUR"}}
        """
        .formatted(couponCode, amountMinor);
  }

  private static ResponseDefinitionBuilder discounts(String... discounts) {
    return okJson("{\"discounts\": [" + String.join(",", discounts) + "]}");
  }

  /** Asked without a Coupon, Promotions answers these Discounts: the running Campaigns'. */
  static void stubCampaigns(String... discounts) {
    DOWNSTREAM.stubFor(post(EVALUATE_PATH).atPriority(5).willReturn(discounts(discounts)));
  }

  /** Asked with a Coupon, Promotions answers these Discounts: the Campaigns', then the Coupon's. */
  static void stubCouponEvaluation(String... discounts) {
    stubCouponEvaluation(discounts(discounts));
  }

  static void stubCouponEvaluation(ResponseDefinitionBuilder response) {
    DOWNSTREAM.stubFor(
        post(EVALUATE_PATH)
            .withRequestBody(matchingJsonPath("$.couponCode"))
            .atPriority(1)
            .willReturn(response));
  }

  /** No Campaign runs, and the Coupon {@code couponCode} takes {@code discountMinor} EUR off. */
  static void stubDiscount(String couponCode, long discountMinor) {
    stubCouponEvaluation(couponDiscount(couponCode, discountMinor));
  }

  /** Promotions finds the Coupon doesn't apply, for {@code reason}. */
  static void stubRejectedCoupon(String reason) {
    stubCouponEvaluation(
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

  /** Promotions answers every evaluation, with a Coupon or without, so. */
  static void stubEvaluation(ResponseDefinitionBuilder response) {
    DOWNSTREAM.stubFor(post(EVALUATE_PATH).atPriority(0).willReturn(response));
  }

  /**
   * Every downstream answers as starting a Checkout Session for two Pixel 9s needs, with no
   * Campaign running.
   */
  static void stubSuccessfulCheckout() {
    stubServiceToken("checkout-token-1");
    stubCart("{\"variantId\": \"PHN-PIXEL-9\", \"quantity\": 2}");
    stubVariant("PHN-PIXEL-9", 79900);
    stubCampaigns();
    stubReserve();
    stubRelease();
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
