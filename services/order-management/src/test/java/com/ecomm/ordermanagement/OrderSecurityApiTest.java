package com.ecomm.ordermanagement;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Only Orchestration places Orders and changes their status; only a Customer reads their Orders.
 * Every endpoint needs a token.
 */
class OrderSecurityApiTest extends OrderApiTest {

  @Test
  void everyEndpointNeedsAToken() {
    var id = placed().id();

    expect(HttpStatus.UNAUTHORIZED, HttpMethod.POST, "/orders", null, twoLineOrder(CUSTOMER));
    expect(HttpStatus.UNAUTHORIZED, HttpMethod.GET, "/orders", null, null);
    expect(HttpStatus.UNAUTHORIZED, HttpMethod.GET, "/orders/" + id, null, null);
    expect(
        HttpStatus.UNAUTHORIZED,
        HttpMethod.PATCH,
        "/orders/" + id + "/status",
        null,
        statusChange(CUSTOMER, "PAID"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "STAFF", "CHECKOUT"})
  void onlyOrchestrationCanPlaceAndChangeOrders(String role) {
    var id = placed().id();
    var token = FakeKeycloak.token(CUSTOMER, role);

    expect(HttpStatus.FORBIDDEN, HttpMethod.POST, "/orders", token, twoLineOrder(CUSTOMER));
    expect(
        HttpStatus.FORBIDDEN,
        HttpMethod.PATCH,
        "/orders/" + id + "/status",
        token,
        statusChange(CUSTOMER, "PAID"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"CHECKOUT", "ORCHESTRATION", "STAFF"})
  void onlyACustomerCanReadOrders(String role) {
    var id = placed().id();
    var token = FakeKeycloak.token(CUSTOMER, role);

    expect(HttpStatus.FORBIDDEN, HttpMethod.GET, "/orders", token, null);
    expect(HttpStatus.FORBIDDEN, HttpMethod.GET, "/orders/" + id, token, null);
  }

  private void expect(HttpStatus status, HttpMethod method, String uri, String token, String body) {
    var request =
        http.method(method)
            .uri(uri)
            .headers(
                h -> {
                  if (token != null) {
                    h.setBearerAuth(token);
                  }
                });
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).body(body);
    }
    request
        .exchange()
        .expectStatus()
        .isEqualTo(status)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }
}
