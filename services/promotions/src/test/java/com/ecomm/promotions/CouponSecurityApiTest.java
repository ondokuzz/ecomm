package com.ecomm.promotions;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Only Staff see or change Coupons: a Customer or Checkout gets 403, no token 401. */
class CouponSecurityApiTest extends PromotionsApiTest {

  @ParameterizedTest
  @CsvSource({
    "GET, /coupons",
    "GET, /coupons/WELCOME10",
    "POST, /coupons",
    "PUT, /coupons/WELCOME10",
    "DELETE, /coupons/WELCOME10"
  })
  void aCustomerOrCheckoutIsForbidden(String method, String path) {
    for (var token :
        new String[] {customerToken(), checkoutToken(), FakeKeycloak.token("someone")}) {
      send(method, path, token)
          .expectStatus()
          .isForbidden()
          .expectHeader()
          .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
    read("WELCOME10").expectStatus().isOk();
  }

  @ParameterizedTest
  @CsvSource({
    "GET, /coupons",
    "GET, /coupons/WELCOME10",
    "POST, /coupons",
    "PUT, /coupons/WELCOME10",
    "DELETE, /coupons/WELCOME10"
  })
  void noTokenIsUnauthorized(String method, String path) {
    send(method, path, null)
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  private RestTestClient.ResponseSpec send(String method, String path, String token) {
    return http.method(HttpMethod.valueOf(method))
        .uri(path)
        .headers(
            h -> {
              if (token != null) {
                h.setBearerAuth(token);
              }
            })
        .contentType(MediaType.APPLICATION_JSON)
        .body(percentOff("WELCOME10", 50))
        .exchange();
  }
}
