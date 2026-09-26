package com.ecomm.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/** A Cart belongs to the Customer whose token made it; nobody else can see or change it. */
class CartIsolationApiTest extends CartApiTest {

  @Test
  void aCustomerSeesOnlyTheirOwnCart() {
    setQuantity("customer-alice", "PHN-PIXEL-9", 2).expectStatus().isOk();
    setQuantity("customer-bob", "AUD-JBL-FLIP-6", 1).expectStatus().isOk();

    assertThat(itemsOf("customer-alice")).containsExactly(new CartItemView("PHN-PIXEL-9", 2));
    assertThat(itemsOf("customer-bob")).containsExactly(new CartItemView("AUD-JBL-FLIP-6", 1));
  }

  @Test
  void anotherCustomerCannotChangeACart() {
    setQuantity("customer-carol", "PHN-PIXEL-9", 2).expectStatus().isOk();
    setQuantity("customer-carol", "AUD-JBL-FLIP-6", 1).expectStatus().isOk();

    setQuantity("customer-dave", "PHN-PIXEL-9", 9).expectStatus().isOk();
    http.delete()
        .uri("/cart/items/{variantId}", "AUD-JBL-FLIP-6")
        .headers(h -> h.setBearerAuth(tokenFor("customer-dave")))
        .exchange()
        .expectStatus()
        .isOk();
    http.delete()
        .uri("/cart")
        .headers(h -> h.setBearerAuth(tokenFor("customer-dave")))
        .exchange()
        .expectStatus()
        .isNoContent();

    assertThat(itemsOf("customer-carol"))
        .containsExactly(new CartItemView("AUD-JBL-FLIP-6", 1), new CartItemView("PHN-PIXEL-9", 2));
  }

  static Stream<Arguments> everyEndpoint() {
    return Stream.of(
        Arguments.of(HttpMethod.GET, "/cart"),
        Arguments.of(HttpMethod.PUT, "/cart/items/PHN-PIXEL-9"),
        Arguments.of(HttpMethod.DELETE, "/cart/items/PHN-PIXEL-9"),
        Arguments.of(HttpMethod.DELETE, "/cart"));
  }

  @ParameterizedTest(name = "{0} {1}")
  @MethodSource("everyEndpoint")
  void everyEndpointNeedsAToken(HttpMethod method, String path) {
    http.method(method)
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"quantity\": 1}")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }
}
