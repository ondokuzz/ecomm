package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** A Customer reads back an Order Checkout placed for them, and nobody else's. */
class ReadOrderApiTest extends OrderApiTest {

  @Test
  void aPlacedOrderCanBeReadBackAtItsLocation() {
    var created = place(twoLineOrder(CUSTOMER)).expectBody(OrderView.class).returnResult();
    var location = created.getResponseHeaders().getLocation();
    var order = created.getResponseBody();

    assertThat(location).hasToString("/orders/" + order.id());
    http.get()
        .uri(location)
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(OrderView.class)
        .isEqualTo(order);
  }

  @ParameterizedTest
  @ValueSource(strings = {"6f1c2d3e-0000-4000-8000-000000000000", "not-an-order-id"})
  void anUnknownOrderIsNotFound(String id) {
    http.get()
        .uri("/orders/{id}", id)
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void anotherCustomersOrderIsNotFound() {
    var id = placed().id();

    http.get()
        .uri("/orders/{id}", id)
        .headers(h -> h.setBearerAuth(tokenOf("customer-7")))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }
}
