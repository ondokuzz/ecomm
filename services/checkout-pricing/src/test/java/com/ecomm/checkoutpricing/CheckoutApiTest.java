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
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Base for HTTP-seam tests. One WireMock server stands in for every service Checkout calls, and for
 * Keycloak's token endpoint; each test starts with no stubs and no cached Checkout token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(CheckoutApiTest.Clocks.class)
abstract class CheckoutApiTest {

  static final String TOKEN_PATH = "/realms/ecomm/protocol/openid-connect/token";
  static final String CUSTOMER_ID = "customer-42";
  static final String ORDER_ID = "7f1c2a3b-0000-4000-8000-000000000001";

  static final WireMockServer DOWNSTREAM = new WireMockServer(wireMockConfig().dynamicPort());

  static {
    DOWNSTREAM.start();
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
    registry.add(
        "spring.security.oauth2.client.provider.keycloak.token-uri", () -> url + TOKEN_PATH);
    registry.add("spring.security.oauth2.client.registration.checkout.client-secret", () -> "test");
  }

  /** A clock the tests move forward, to bring Checkout's cached token near its expiry. */
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
  void resetDownstream() {
    DOWNSTREAM.resetAll();
    authorizedClients.removeAuthorizedClient("checkout", "checkout");
    clock.reset();
  }

  /** The token of {@code customer-42}, the Customer every test checks out as. */
  static String customerToken() {
    return FakeKeycloak.token(CUSTOMER_ID, "CUSTOMER");
  }

  RestTestClient.ResponseSpec checkout() {
    return checkout(customerToken());
  }

  RestTestClient.ResponseSpec checkout(String token) {
    return http.post().uri("/checkout").headers(h -> h.setBearerAuth(token)).exchange();
  }

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

  static void stubDecrement() {
    stubDecrement(okJson("[]"));
  }

  static void stubDecrement(ResponseDefinitionBuilder response) {
    DOWNSTREAM.stubFor(post("/stock/decrement").willReturn(response));
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

  static void stubPayment(ResponseDefinitionBuilder response) {
    DOWNSTREAM.stubFor(post("/payments").willReturn(response));
  }

  /** Every downstream answers as a successful checkout of one Pixel 9 needs. */
  static void stubSuccessfulCheckout() {
    stubServiceToken("checkout-token-1");
    stubCart("{\"variantId\": \"PHN-PIXEL-9\", \"quantity\": 2}");
    stubVariant("PHN-PIXEL-9", 79900);
    stubPlaceOrder();
    stubDecrement();
    stubPayment();
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
