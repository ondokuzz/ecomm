package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

/** A Customer lists their own Orders, newest first, and never sees anyone else's. */
class ListOrdersApiTest extends OrderApiTest {

  private static final ParameterizedTypeReference<List<OrderView>> ORDERS =
      new ParameterizedTypeReference<>() {};

  @Test
  void aCustomerSeesOnlyTheirOwnOrdersNewestFirst() {
    var alice = FakeKeycloak.token("customer-alice", "CUSTOMER");
    var bob = FakeKeycloak.token("customer-bob", "CUSTOMER");
    var first = placed(alice);
    placed(bob);
    var second = placed(alice);

    assertThat(list(alice)).extracting(OrderView::id).containsExactly(second.id(), first.id());
  }

  @Test
  void aListedOrderMatchesTheOrderItself() {
    var carol = FakeKeycloak.token("customer-carol", "CUSTOMER");
    var order = placed(carol);

    assertThat(list(carol)).containsExactly(order);
  }

  @Test
  void aCustomerWithoutOrdersGetsAnEmptyList() {
    assertThat(list(FakeKeycloak.token("customer-new", "CUSTOMER"))).isEmpty();
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
