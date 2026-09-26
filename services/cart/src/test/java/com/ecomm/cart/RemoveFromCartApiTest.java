package com.ecomm.cart;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A Customer takes one Variant out of their Cart, or empties it. */
class RemoveFromCartApiTest extends CartApiTest {

  @Test
  void removingAVariantLeavesTheRestOfTheCart() {
    setQuantity("customer-remove", "PHN-PIXEL-9", 2).expectStatus().isOk();
    setQuantity("customer-remove", "AUD-JBL-FLIP-6", 1).expectStatus().isOk();

    http.delete()
        .uri("/cart/items/{variantId}", "PHN-PIXEL-9")
        .headers(h -> h.setBearerAuth(tokenFor("customer-remove")))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items.length()")
        .isEqualTo(1);

    assertThat(itemsOf("customer-remove")).containsExactly(new CartItemView("AUD-JBL-FLIP-6", 1));
  }

  @Test
  void removingAVariantThatIsNotInTheCartChangesNothing() {
    setQuantity("customer-remove-absent", "PHN-PIXEL-9", 2).expectStatus().isOk();

    http.delete()
        .uri("/cart/items/{variantId}", "AUD-JBL-FLIP-6")
        .headers(h -> h.setBearerAuth(tokenFor("customer-remove-absent")))
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(itemsOf("customer-remove-absent"))
        .containsExactly(new CartItemView("PHN-PIXEL-9", 2));
  }

  @Test
  void clearingTheCartEmptiesIt() {
    setQuantity("customer-clear", "PHN-PIXEL-9", 2).expectStatus().isOk();
    setQuantity("customer-clear", "AUD-JBL-FLIP-6", 1).expectStatus().isOk();

    http.delete()
        .uri("/cart")
        .headers(h -> h.setBearerAuth(tokenFor("customer-clear")))
        .exchange()
        .expectStatus()
        .isNoContent();

    assertThat(itemsOf("customer-clear")).isEmpty();
  }
}
