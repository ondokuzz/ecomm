package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Only a Customer checks out, and only their own Cart. */
class CheckoutSecurityApiTest extends CheckoutApiTest {

  @Test
  void withoutATokenCheckoutIsUnauthorized() {
    stubSuccessfulCheckout();

    http.post()
        .uri("/checkout")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void staffHaveNoCartToCheckOut() {
    stubSuccessfulCheckout();

    checkout(FakeKeycloak.token("staff-1", "STAFF")).expectStatus().isForbidden();

    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void anotherServiceCantCheckOut() {
    stubSuccessfulCheckout();

    checkout(FakeKeycloak.token("checkout", "CHECKOUT")).expectStatus().isForbidden();
  }
}
