package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * A Customer finds their Order by the Order reference in their emails, such as {@code #EA2CA751},
 * as a page of their Orders that match, and never finds anyone else's.
 */
class FindOrderByReferenceApiTest extends OrderApiTest {

  record PageView(List<OrderView> items, int page, int size, long total) {}

  @Test
  void aCustomerFindsTheirOrderByItsReference() {
    var order = placed("customer-erin");
    placed("customer-erin");

    var page = find(tokenOf("customer-erin"), referenceOf(order));

    assertThat(page.items()).containsExactly(order);
    assertThat(page.total()).isEqualTo(1);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
  }

  @ParameterizedTest
  @ValueSource(strings = {"#%s", "%s", "#%S", "%S"})
  void theReferenceIsFoundWithOrWithoutItsHashInEitherCase(String form) {
    var order = placed("customer-frank");
    var idStart = order.id().substring(0, 8);

    assertThat(find(tokenOf("customer-frank"), form.formatted(idStart)).items())
        .containsExactly(order);
  }

  @Test
  void anotherCustomersOrderIsNeverFound() {
    var theirs = placed("customer-grace");

    var page = find(tokenOf("customer-heidi"), referenceOf(theirs));

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
  }

  @Test
  void aReferenceNoOrderHasIsAnEmptyPage() {
    placed("customer-ivan");

    var page = find(tokenOf("customer-ivan"), "#00000000");

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "#",
        "EA2CA75",
        "EA2CA7510",
        "#EA2CA75G",
        "##EA2CA751",
        "RMA-EA2CA751",
        "EA2C-A75"
      })
  void anythingButEightHexDigitsIsAnInvalidReference(String reference) {
    http.get()
        .uri(b -> b.path("/orders").queryParam("reference", "{reference}").build(reference))
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo("invalidReference");
  }

  private PageView find(String token, String reference) {
    return http.get()
        .uri(b -> b.path("/orders").queryParam("reference", "{reference}").build(reference))
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(PageView.class)
        .returnResult()
        .getResponseBody();
  }

  /** The Order reference the Storefront and the emails show, such as {@code #EA2CA751}. */
  private static String referenceOf(OrderView order) {
    return "#" + order.id().substring(0, 8).toUpperCase(Locale.ROOT);
  }
}
