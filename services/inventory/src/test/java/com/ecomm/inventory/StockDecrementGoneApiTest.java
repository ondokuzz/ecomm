package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Checkout takes Stock only by committing a Reservation now, so the direct decrement it used before
 * Checkout Sessions is gone, even for Checkout's own token.
 */
class StockDecrementGoneApiTest extends InventoryApiTest {

  @Test
  void decrementingStockDirectlyIsGone() {
    http.post()
        .uri("/stock/decrement")
        .headers(h -> h.setBearerAuth(checkoutToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"items": [{"variantId": "TEST-AUTH", "quantity": 1}]}
            """)
        .exchange()
        .expectStatus()
        .is4xxClientError();

    assertThat(quantityOf("TEST-AUTH")).isEqualTo(8);
  }
}
