package com.ecomm.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/**
 * Public reads pass without a token; every other routed request needs a valid {@code ecomm} token,
 * or gets a 401 at the gateway and never reaches the service.
 */
class EdgeAuthenticationApiTest extends GatewayApiTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/catalog/products",
        "/api/catalog/products/PHN-PIXEL-9",
        "/api/catalog/variants",
        "/api/catalog/categories",
        "/api/inventory/stock/PHN-PIXEL-9"
      })
  void publicReadsNeedNoToken(String path) {
    http.get().uri(path).exchange().expectStatus().isOk();

    assertThat(forwarded()).hasSize(1);
  }

  @ParameterizedTest
  @CsvSource({
    "POST, /api/catalog/products",
    "PUT, /api/catalog/products/PHN-PIXEL-9",
    "DELETE, /api/catalog/products/PHN-PIXEL-9",
    "GET, /api/cart/cart",
    "PUT, /api/cart/cart/items/PHN-PIXEL-9",
    "POST, /api/checkout-pricing/checkout/sessions",
    "POST, /api/checkout-pricing/checkout/sessions/s-1/pay",
    "GET, /api/order-management/orders",
    "GET, /api/order-management/orders/7f1c"
  })
  void everythingElseIsUnauthorizedWithoutAToken(String method, String path) {
    http.method(HttpMethod.valueOf(method))
        .uri(path)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401);

    assertThat(forwarded()).isEmpty();
  }

  @Test
  void aTokenSignedByAnUnknownKeyIsUnauthorized() {
    expectUnauthorized(FakeKeycloak.tokenSignedByUnknownKey("customer-42"));
  }

  @Test
  void aTokenFromAnotherIssuerIsUnauthorized() {
    expectUnauthorized(
        FakeKeycloak.tokenFromIssuer("http://keycloak:8080/realms/ecomm", "customer-42"));
  }

  @Test
  void aMalformedTokenIsUnauthorized() {
    expectUnauthorized("not-a-jwt");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/cart/cart", "/api/catalog/products"})
  void theAuthorizationHeaderIsForwardedUntouched(String path) {
    var token = customerToken();

    http.get().uri(path).headers(h -> h.setBearerAuth(token)).exchange().expectStatus().isOk();

    assertThat(forwarded())
        .singleElement()
        .satisfies(
            call -> assertThat(call.getHeader("Authorization")).isEqualTo("Bearer " + token));
  }

  @Test
  void healthNeedsNoToken() {
    http.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }

  private void expectUnauthorized(String token) {
    http.get()
        .uri("/api/cart/cart")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(forwarded()).isEmpty();
  }
}
