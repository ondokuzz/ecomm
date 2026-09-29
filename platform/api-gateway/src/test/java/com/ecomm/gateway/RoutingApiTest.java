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
    "/api/cart/cart, /cart",
    "/api/checkout-pricing/checkout, /checkout",
    "/api/order-management/orders/7f1c, /orders/7f1c"
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
    "POST, /api/inventory/reservations/r-1/commit",
    "DELETE, /api/inventory/reservations/r-1",
    "POST, /api/order-management/orders",
    "PATCH, /api/order-management/orders/7f1c/status",
    "POST, /api/order-management/orders/",
    "POST, /api/payment/payments",
    "GET, /api/payment/payments/p-1",
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
    "POST, /api/inventory/stock/decrement",
    "POST, /api/order-management/orders",
    "POST, /api/payment/payments"
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
