package com.ecomm.cart;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** A Customer puts a Variant in their Cart, or changes how many of it they want. */
class SetQuantityApiTest extends CartApiTest {

  @Test
  void settingAQuantityAddsTheVariantToTheCart() {
    setQuantity("customer-add", "PHN-PIXEL-9", 2)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items[0].variantId")
        .isEqualTo("PHN-PIXEL-9")
        .jsonPath("$.items[0].quantity")
        .isEqualTo(2);

    assertThat(itemsOf("customer-add")).containsExactly(new CartItemView("PHN-PIXEL-9", 2));
  }

  @Test
  void settingAQuantityAgainReplacesIt() {
    setQuantity("customer-update", "PHN-PIXEL-9", 2).expectStatus().isOk();
    setQuantity("customer-update", "AUD-JBL-FLIP-6", 1).expectStatus().isOk();
    setQuantity("customer-update", "PHN-PIXEL-9", 5).expectStatus().isOk();

    assertThat(itemsOf("customer-update"))
        .containsExactly(new CartItemView("AUD-JBL-FLIP-6", 1), new CartItemView("PHN-PIXEL-9", 5));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {"quantity": 0}
        """,
        """
        {"quantity": -1}
        """,
        """
        {"quantity": 1.5}
        """,
        """
        {"quantity": "2"}
        """,
        """
        {"quantity": true}
        """,
        """
        {"quantity": null}
        """,
        """
        {}
        """,
        """
        {"quantity": 3000000000}
        """,
        "not json"
      })
  void anythingButAPositiveIntegerQuantityIsABadRequest(String body) {
    setQuantity("customer-invalid", "PHN-PIXEL-9", 1).expectStatus().isOk();

    setQuantity("customer-invalid", "PHN-PIXEL-9", body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(itemsOf("customer-invalid")).containsExactly(new CartItemView("PHN-PIXEL-9", 1));
  }
}
