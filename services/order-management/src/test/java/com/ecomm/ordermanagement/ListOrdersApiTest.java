package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

/** A Customer lists their own Orders, newest first, and never sees anyone else's. */
class ListOrdersApiTest extends OrderApiTest {

  private static final ParameterizedTypeReference<List<OrderView>> ORDERS =
      new ParameterizedTypeReference<>() {};

  @Test
  void aCustomerSeesOnlyTheirOwnOrdersNewestFirst() {
    var first = placed("customer-alice");
    placed("customer-bob");
    var second = placed("customer-alice");

    assertThat(list(tokenOf("customer-alice")))
        .extracting(OrderView::id)
        .containsExactly(second.id(), first.id());
  }

  @Test
  void aListedOrderMatchesTheOrderItself() {
    var order = placed("customer-carol");

    assertThat(list(tokenOf("customer-carol"))).containsExactly(order);
  }

  @Test
  void aCustomerWithoutOrdersGetsAnEmptyList() {
    assertThat(list(tokenOf("customer-new"))).isEmpty();
  }

  private List<OrderView> list(String token) {
    return http.get()
        .uri("/orders")
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(ORDERS)
        .returnResult()
        .getResponseBody();
  }
}
