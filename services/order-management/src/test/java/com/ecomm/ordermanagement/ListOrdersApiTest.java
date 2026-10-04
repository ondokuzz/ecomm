package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * A Customer lists their own Orders a page at a time, newest first, with how many they have in all,
 * and never sees anyone else's.
 */
class ListOrdersApiTest extends OrderApiTest {

  record PageView(List<OrderView> items, int page, int size, long total) {}

  @Test
  void aCustomerSeesOnlyTheirOwnOrdersNewestFirst() {
    var first = placed("customer-alice");
    placed("customer-bob");
    var second = placed("customer-alice");

    var page = list(tokenOf("customer-alice"), "");

    assertThat(page.items()).extracting(OrderView::id).containsExactly(second.id(), first.id());
    assertThat(page.total()).isEqualTo(2);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
  }

  @Test
  void aListedOrderMatchesTheOrderItself() {
    var order = placed("customer-carol");

    assertThat(list(tokenOf("customer-carol"), "").items()).containsExactly(order);
  }

  @Test
  void aCustomerWithoutOrdersGetsAnEmptyPage() {
    var page = list(tokenOf("customer-new"), "");

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
  }

  @Test
  void theOrdersComeAPageAtATimeWithTheirTotal() {
    var oldest = placed("customer-dave");
    var middle = placed("customer-dave");
    var newest = placed("customer-dave");
    var token = tokenOf("customer-dave");

    var first = list(token, "?page=0&size=2");
    var second = list(token, "?page=1&size=2");
    var beyond = list(token, "?page=2&size=2");

    assertThat(first.items()).extracting(OrderView::id).containsExactly(newest.id(), middle.id());
    assertThat(second.items()).extracting(OrderView::id).containsExactly(oldest.id());
    assertThat(beyond.items()).isEmpty();
    assertThat(List.of(first, second, beyond)).extracting(PageView::total).containsOnly(3L);
    assertThat(second.page()).isEqualTo(1);
    assertThat(second.size()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?page=first", "?size=2.5"})
  void anInvalidPageIsABadRequest(String query) {
    http.get()
        .uri("/orders" + query)
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  private PageView list(String token, String query) {
    return http.get()
        .uri("/orders" + query)
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(PageView.class)
        .returnResult()
        .getResponseBody();
  }
}
