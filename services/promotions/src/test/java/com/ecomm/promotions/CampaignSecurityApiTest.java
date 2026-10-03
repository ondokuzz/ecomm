package com.ecomm.promotions;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Only Staff see or change Campaigns: a Customer or Checkout gets 403, no token 401. */
class CampaignSecurityApiTest extends PromotionsApiTest {

  private static final String SOME_ID = "7f1c2a3b-0000-4000-8000-000000000001";

  @ParameterizedTest
  @CsvSource({
    "GET, /campaigns",
    "GET, /campaigns/" + SOME_ID,
    "POST, /campaigns",
    "PUT, /campaigns/" + SOME_ID,
    "DELETE, /campaigns/" + SOME_ID
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
  }

  @ParameterizedTest
  @CsvSource({
    "GET, /campaigns",
    "GET, /campaigns/" + SOME_ID,
    "POST, /campaigns",
    "PUT, /campaigns/" + SOME_ID,
    "DELETE, /campaigns/" + SOME_ID
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
        .body(campaign("Forbidden", 700, 50))
        .exchange();
  }
}
