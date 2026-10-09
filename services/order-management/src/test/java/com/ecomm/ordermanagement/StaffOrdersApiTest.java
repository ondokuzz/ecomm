package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ecomm.commons.security.FakeKeycloak;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** Staff read any Customer's Order, with who it belongs to, but can't change it. */
class StaffOrdersApiTest extends OrderApiTest {

  record PageView(List<OrderView> items, int page, int size, long total) {}

  static String staffToken() {
    return FakeKeycloak.token("staff-1", "STAFF");
  }

  @Test
  void staffReadAnotherCustomersOrderWithItsCustomer() {
    var placed = placed("customer-erin");
    var paid =
        changeStatus(orchestrationToken(), placed.id(), statusChange("customer-erin", "PAID"));
    paid.expectStatus().isOk();

    var order = staffOrder(placed.id());

    assertThat(order.customerId()).isEqualTo("customer-erin");
    assertThat(order.status()).isEqualTo("PAID");
    assertThat(order.lines()).isEqualTo(placed.lines());
    assertThat(order.total()).isEqualTo(placed.total());
    assertThat(order.statusHistory())
        .extracting(HistoryEntryView::status, HistoryEntryView::changedBy)
        .containsExactly(tuple("PLACED", "ORCHESTRATION"), tuple("PAID", "ORCHESTRATION"));
  }

  @Test
  void anUnknownOrAMalformedIdIsNotFound() {
    for (var id : new String[] {"00000000-0000-4000-8000-000000000000", "not-a-uuid"}) {
      http.get()
          .uri("/staff/orders/{id}", id)
          .headers(h -> h.setBearerAuth(staffToken()))
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectHeader()
          .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
  }

  @Test
  void theListIsEveryCustomersOrdersNewestFirstAPageAtATime() {
    var oldest = placed("customer-frank");
    var middle = placed("customer-gina");
    var newest = placed("customer-frank");

    var first = list("?size=2");
    var second = list("?page=1&size=2");

    assertThat(first.items()).extracting(OrderView::id).containsExactly(newest.id(), middle.id());
    assertThat(second.items()).extracting(OrderView::id).startsWith(oldest.id());
    assertThat(first.items().getFirst()).isEqualTo(newest);
    assertThat(first.total()).isEqualTo(second.total()).isGreaterThanOrEqualTo(3);
    assertThat(second.page()).isEqualTo(1);
    assertThat(second.size()).isEqualTo(2);
  }

  @Test
  void theListFiltersByCustomer() {
    var customer = uniqueCustomer();
    var first = placed(customer);
    placed(uniqueCustomer());
    var second = placed(customer);

    var page = list("?customerId=" + customer);

    assertThat(page.items()).extracting(OrderView::id).containsExactly(second.id(), first.id());
    assertThat(page.total()).isEqualTo(2);
  }

  @Test
  void theListFiltersByStatus() {
    var customer = uniqueCustomer();
    var cancelled = placed(customer);
    move(customer, cancelled, "CANCELLED");
    placed(customer);

    var page = list("?status=CANCELLED&size=100");

    assertThat(page.items().getFirst().id()).isEqualTo(cancelled.id());
    assertThat(page.items()).extracting(OrderView::status).containsOnly("CANCELLED");
  }

  @Test
  void theListFiltersByWhenOrdersWerePlacedFromInclusiveToExclusive() {
    var first = staffOrder(placed(uniqueCustomer()).id());
    var second = staffOrder(placed(uniqueCustomer()).id());
    var third = staffOrder(placed(uniqueCustomer()).id());

    var between = list("?placedFrom=" + second.placedAt() + "&placedTo=" + third.placedAt());
    var from = list("?placedFrom=" + second.placedAt());
    var to = list("?placedTo=" + second.placedAt());

    assertThat(between.items()).extracting(OrderView::id).containsExactly(second.id());
    assertThat(between.total()).isEqualTo(1);
    assertThat(from.items()).extracting(OrderView::id).containsExactly(third.id(), second.id());
    assertThat(to.items().getFirst().id()).isEqualTo(first.id());
  }

  @Test
  void theListFindsAnOrderByItsOrderReferenceOrItsFullId() {
    var order = placed(uniqueCustomer());
    placed(uniqueCustomer());
    var reference = order.id().substring(0, 8).toUpperCase();

    var byReference = list("?idPrefix=" + reference);
    var byId = list("?idPrefix=" + order.id());

    assertThat(byReference.items()).extracting(OrderView::id).containsExactly(order.id());
    assertThat(byReference.total()).isEqualTo(1);
    assertThat(byId.items()).extracting(OrderView::id).containsExactly(order.id());
  }

  @Test
  void theFiltersCombine() {
    var customer = uniqueCustomer();
    var placedOnly = staffOrder(placed(customer).id());
    var paid = staffOrder(placed(customer).id());
    move(customer, paid, "PAID");
    var paidLater = staffOrder(placed(customer).id());
    move(customer, paidLater, "PAID");
    var otherCustomersPaid = placed(uniqueCustomer());
    move(otherCustomersPaid.customerId(), otherCustomersPaid, "PAID");

    var byCustomerAndStatus = list("?customerId=" + customer + "&status=PAID");
    var everything =
        list(
            "?customerId=%s&status=PAID&placedFrom=%s&placedTo=%s&idPrefix=%s"
                .formatted(
                    customer,
                    placedOnly.placedAt(),
                    paidLater.placedAt(),
                    paid.id().substring(0, 8)));
    var none = list("?customerId=" + customer + "&status=CANCELLED");

    assertThat(byCustomerAndStatus.items())
        .extracting(OrderView::id)
        .containsExactly(paidLater.id(), paid.id());
    assertThat(byCustomerAndStatus.total()).isEqualTo(2);
    assertThat(everything.items()).extracting(OrderView::id).containsExactly(paid.id());
    assertThat(everything.total()).isEqualTo(1);
    assertThat(none.items()).isEmpty();
    assertThat(none.total()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "?idPrefix=",
        "?idPrefix=3F2A_",
        "?idPrefix=%253F2A",
        "?idPrefix=3f2a9c1b-0000-4000-8000-0000000000001",
        "?status=LOST",
        "?status=paid",
        "?placedFrom=yesterday",
        "?placedTo=2026-10-05",
        "?page=-1",
        "?size=101"
      })
  void anInvalidQueryIsABadRequest(String query) {
    http.get()
        .uri("/staff/orders" + query)
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "CHECKOUT"})
  void onlyStaffCanBrowseOrders(String role) {
    var order = placed(CUSTOMER);
    var token = FakeKeycloak.token(CUSTOMER, role);

    for (var uri : List.of("/staff/orders", "/staff/orders/" + order.id())) {
      http.get()
          .uri(uri)
          .headers(h -> h.setBearerAuth(token))
          .exchange()
          .expectStatus()
          .isForbidden()
          .expectHeader()
          .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
  }

  @Test
  void browsingNeedsAToken() {
    var order = placed();

    for (var uri : List.of("/staff/orders", "/staff/orders/" + order.id())) {
      http.get().uri(uri).exchange().expectStatus().isUnauthorized();
    }
  }

  /** A Customer no other test has placed Orders for, since every test class shares one database. */
  private static String uniqueCustomer() {
    return "customer-" + UUID.randomUUID();
  }

  /** Moves the Customer's Order to {@code status} as Checkout. */
  private void move(String customerId, OrderView order, String status) {
    changeStatus(orchestrationToken(), order.id(), statusChange(customerId, status))
        .expectStatus()
        .isOk();
  }

  private PageView list(String query) {
    return http.get()
        .uri("/staff/orders" + query)
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(PageView.class)
        .returnResult()
        .getResponseBody();
  }

  private OrderView staffOrder(String id) {
    return http.get()
        .uri("/staff/orders/{id}", id)
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(OrderView.class)
        .returnResult()
        .getResponseBody();
  }
}
