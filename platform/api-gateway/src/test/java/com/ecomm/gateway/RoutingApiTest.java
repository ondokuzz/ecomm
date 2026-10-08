package com.ecomm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/**
 * {@code /api/<service>/**} reaches that service with the prefix stripped, while internal endpoints
 * and anything else stay hidden behind a 404.
 */
class RoutingApiTest extends GatewayApiTest {

  @ParameterizedTest
  @CsvSource({
    "/api/catalog/products, /products",
    "/api/inventory/stock/PHN-PIXEL-9, /stock/PHN-PIXEL-9",
    "/api/inventory/stock/PHN-PIXEL-9/movements?page=1, /stock/PHN-PIXEL-9/movements?page=1",
    "/api/cart/cart, /cart",
    "/api/checkout-pricing/checkout/sessions/current, /checkout/sessions/current",
    "/api/order-management/orders/7f1c, /orders/7f1c",
    "/api/order-management/staff/orders?status=PAID&idPrefix=3f2a, /staff/orders?status=PAID&idPrefix=3f2a",
    "/api/order-management/staff/orders/7f1c, /staff/orders/7f1c",
    "/api/promotions/coupons/WELCOME10, /coupons/WELCOME10",
    "/api/promotions/campaigns/7f1c, /campaigns/7f1c",
    "/api/search-discovery/search?category=phones&sort=newest, /search?category=phones&sort=newest",
    "/api/reviews-ratings/products/PHN-PIXEL-9/reviews?page=1, /products/PHN-PIXEL-9/reviews?page=1"
  })
  void eachServiceIsReachedWithThePrefixStripped(String path, String downstreamPath) {
    http.get()
        .uri(path)
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("from downstream");

    DOWNSTREAM.verify(getRequestedFor(urlEqualTo(downstreamPath)));
  }

  @Test
  void theQueryStringIsForwarded() {
    http.get().uri("/api/catalog/products?category=phones").exchange().expectStatus().isOk();

    DOWNSTREAM.verify(getRequestedFor(urlEqualTo("/products?category=phones")));
  }

  @ParameterizedTest
  @CsvSource({
    "POST, /api/inventory/stock/decrement",
    "POST, /api/inventory/stock/decrement/",
    "POST, /api/inventory/reservations",
    "POST, /api/inventory/reservations/",
    "POST, /api/inventory/reservations/r-1/commit",
    "DELETE, /api/inventory/reservations/r-1",
    "POST, /api/order-management/orders",
    "PATCH, /api/order-management/orders/7f1c/status",
    "POST, /api/order-management/orders/",
    "POST, /api/payment/payments",
    "GET, /api/payment/payments/p-1",
    "POST, /api/promotions/discounts/evaluate",
    "POST, /api/promotions/discounts/evaluate/",
    "GET, /api/promotions/discounts/evaluate",
    "GET, /api/catalog/actuator/prometheus",
    "GET, /api/catalog/actuator/health",
    "GET, /api/order-management/actuator",
    "GET, /api/no-such-service/things",
    "GET, /products"
  })
  void internalAndUnknownEndpointsAreNotFoundEvenWithAToken(String method, String path) {
    http.method(HttpMethod.valueOf(method))
        .uri(path)
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(forwarded()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "POST, /api/inventory/reservations",
    "POST, /api/order-management/orders",
    "POST, /api/payment/payments",
    "POST, /api/promotions/discounts/evaluate"
  })
  void internalEndpointsAreNotFoundWithoutAToken(String method, String path) {
    http.method(HttpMethod.valueOf(method)).uri(path).exchange().expectStatus().isNotFound();

    assertThat(forwarded()).isEmpty();
  }

  @Test
  void aCustomersOwnOrdersAreStillRouted() {
    http.get()
        .uri("/api/order-management/orders")
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isOk();

    DOWNSTREAM.verify(getRequestedFor(urlEqualTo("/orders")));
  }

  @Test
  void theGatewaySendsNoCorsHeaders() {
    http.get()
        .uri("/api/catalog/products")
        .header("Origin", "http://evil.example")
        .exchange()
        .expectHeader()
        .doesNotExist("Access-Control-Allow-Origin");
  }
}
