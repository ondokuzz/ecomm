package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Checkout moves an Order along its Order Status lifecycle; illegal moves are a conflict. */
class ChangeOrderStatusApiTest extends OrderApiTest {

  @Test
  void aPlacedOrderCanBePaidAndStaysPaid() {
    var order = placed();

    changeStatus(order.id(), "PAID")
        .expectStatus()
        .isOk()
        .expectBody(OrderView.class)
        .value(paid -> assertThat(paid.status()).isEqualTo("PAID"));
    assertThat(read(order.id()).status()).isEqualTo("PAID");
  }

  @Test
  void anOrderCanFollowTheMainPathToDelivery() {
    var id = placed().id();

    for (var status : new String[] {"PAID", "FULFILLED", "SHIPPED", "DELIVERED"}) {
      changeStatus(id, status).expectStatus().isOk();
    }
    assertThat(read(id).status()).isEqualTo("DELIVERED");
  }

  @Test
  void anIllegalTransitionIsAConflictAndLeavesTheOrderAsItWas() {
    var id = placed().id();

    changeStatus(id, "SHIPPED")
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(read(id).status()).isEqualTo("PLACED");
  }

  @Test
  void aCancelledOrderCannotBePaid() {
    var id = placed().id();
    changeStatus(id, "CANCELLED").expectStatus().isOk();

    changeStatus(id, "PAID").expectStatus().isEqualTo(409);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"customerId\": \"customer-42\"}",
        "{\"customerId\": \"customer-42\", \"status\": \"LOST\"}",
        "{\"customerId\": \"customer-42\", \"status\": 1}",
        "{\"status\": \"PAID\"}",
        "{\"customerId\": \" \", \"status\": \"PAID\"}",
        "{\"customerId\": 42, \"status\": \"PAID\"}",
        "{\"customerId\": \"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\", \"status\": \"PAID\"}",
        "not json"
      })
  void anInvalidChangeIsABadRequest(String body) {
    changeStatus(orchestrationToken(), placed().id(), body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @ParameterizedTest
  @ValueSource(strings = {"6f1c2d3e-0000-4000-8000-000000000000", "not-an-order-id"})
  void anInvalidCustomerIdIsABadRequestWhateverTheOrder(String id) {
    changeStatus(orchestrationToken(), id, statusChange(" ", "PAID"))
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @ParameterizedTest
  @ValueSource(strings = {"6f1c2d3e-0000-4000-8000-000000000000", "not-an-order-id"})
  void changingAnUnknownOrderIsNotFound(String id) {
    changeStatus(id, "PAID")
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void checkoutNamingTheWrongCustomerGetsNotFound() {
    var id = placed().id();

    changeStatus(orchestrationToken(), id, statusChange("customer-7", "CANCELLED"))
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(read(id).status()).isEqualTo("PLACED");
  }

  private OrderView read(String id) {
    return http.get()
        .uri("/orders/{id}", id)
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectBody(OrderView.class)
        .returnResult()
        .getResponseBody();
  }
}
