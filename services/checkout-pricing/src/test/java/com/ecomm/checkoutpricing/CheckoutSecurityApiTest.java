package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/** Only a Customer checks out, and only their own Cart. */
class CheckoutSecurityApiTest extends CheckoutApiTest {

  @ParameterizedTest
  @CsvSource({
    "POST, /checkout/sessions",
    "GET, /checkout/sessions/current",
    "POST, /checkout/sessions/0b7e6a52-0000-4000-8000-000000000009/pay"
  })
  void withoutATokenCheckoutIsUnauthorized(String method, String path) {
    stubSuccessfulCheckout();

    http.method(HttpMethod.valueOf(method))
        .uri(path)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
  }

  @ParameterizedTest
  @CsvSource({
    "POST, /checkout/sessions",
    "GET, /checkout/sessions/current",
    "POST, /checkout/sessions/0b7e6a52-0000-4000-8000-000000000009/pay"
  })
  void staffHaveNoCartToCheckOut(String method, String path) {
    stubSuccessfulCheckout();

    http.method(HttpMethod.valueOf(method))
        .uri(path)
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("staff-1", "STAFF")))
        .exchange()
        .expectStatus()
        .isForbidden();

    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void anotherServiceCantCheckOut() {
    stubSuccessfulCheckout();

    startSession(FakeKeycloak.token("checkout", "CHECKOUT")).expectStatus().isForbidden();
  }

  @Test
  void theOldEndpointIsGone() {
    stubSuccessfulCheckout();

    http.post()
        .uri("/checkout")
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .is4xxClientError();

    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
  }
}
