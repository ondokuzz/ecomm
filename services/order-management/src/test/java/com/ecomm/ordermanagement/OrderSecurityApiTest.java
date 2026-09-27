package com.ecomm.ordermanagement;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Every Order endpoint needs a Customer's token; Staff have no Orders. */
class OrderSecurityApiTest extends OrderApiTest {

  @Test
  void everyEndpointNeedsAToken() {
    var id = placed().id();

    expect(HttpStatus.UNAUTHORIZED, HttpMethod.POST, "/orders", null, TWO_LINE_ORDER);
    expect(HttpStatus.UNAUTHORIZED, HttpMethod.GET, "/orders", null, null);
    expect(HttpStatus.UNAUTHORIZED, HttpMethod.GET, "/orders/" + id, null, null);
    expect(
        HttpStatus.UNAUTHORIZED,
        HttpMethod.PATCH,
        "/orders/" + id + "/status",
        null,
        "{\"status\": \"PAID\"}");
  }

  @Test
  void staffAreForbiddenEverywhere() {
    var id = placed().id();
    var staff = FakeKeycloak.token("staff-1", "STAFF");

    expect(HttpStatus.FORBIDDEN, HttpMethod.POST, "/orders", staff, TWO_LINE_ORDER);
    expect(HttpStatus.FORBIDDEN, HttpMethod.GET, "/orders", staff, null);
    expect(HttpStatus.FORBIDDEN, HttpMethod.GET, "/orders/" + id, staff, null);
    expect(
        HttpStatus.FORBIDDEN,
        HttpMethod.PATCH,
        "/orders/" + id + "/status",
        staff,
        "{\"status\": \"PAID\"}");
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
