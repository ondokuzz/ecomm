package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * The Reservation endpoints are internal: only Checkout, with its own {@code CHECKOUT} token, may
 * call them.
 */
class ReservationSecurityApiTest extends InventoryApiTest {

  @Test
  void everyReservationEndpointNeedsAToken() {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 1));

    expect(HttpStatus.UNAUTHORIZED, null, "/reservations", newReservation(variant));
    expect(
        HttpStatus.UNAUTHORIZED,
        null,
        "/reservations/" + reservation.id() + "/commit",
        customerBody(CUSTOMER));
    expect(
        HttpStatus.UNAUTHORIZED,
        null,
        "/reservations/" + reservation.id() + "/release",
        customerBody(CUSTOMER));

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 9, 10, 1));
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "STAFF"})
  void onlyCheckoutCanReserveCommitAndRelease(String role) {
    var variant = newVariant(10);
    var reservation = reserved(item(variant, 1));
    var token = FakeKeycloak.token(CUSTOMER, role);

    expect(HttpStatus.FORBIDDEN, token, "/reservations", newReservation(variant));
    expect(
        HttpStatus.FORBIDDEN,
        token,
        "/reservations/" + reservation.id() + "/commit",
        customerBody(CUSTOMER));
    expect(
        HttpStatus.FORBIDDEN,
        token,
        "/reservations/" + reservation.id() + "/release",
        customerBody(CUSTOMER));

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 9, 10, 1));
  }

  private String newReservation(String variant) {
    return reservationBody(CUSTOMER, inFifteenMinutes(), item(variant, 1));
  }

  private void expect(HttpStatus status, String token, String uri, String body) {
    http.post()
        .uri(uri)
        .headers(
            h -> {
              if (token != null) {
                h.setBearerAuth(token);
              }
            })
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isEqualTo(status)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }
}
