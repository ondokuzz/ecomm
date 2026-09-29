package com.ecomm.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.security.FakeKeycloak;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Checkout decrements stock for several Variants at once; the batch succeeds or fails whole. */
class DecrementStockApiTest extends InventoryApiTest {

  @Test
  void aBatchDecrementsEveryVariantInIt() {
    decrement(
            """
            {"items": [
              {"variantId": "TEST-BATCH-A", "quantity": 3},
              {"variantId": "TEST-BATCH-B", "quantity": 5}
            ]}
            """)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[?(@.variantId == 'TEST-BATCH-A')].quantity")
        .isEqualTo(7)
        .jsonPath("$[?(@.variantId == 'TEST-BATCH-B')].quantity")
        .isEqualTo(0);

    assertThat(quantityOf("TEST-BATCH-A")).isEqualTo(7);
    assertThat(quantityOf("TEST-BATCH-B")).isZero();
  }

  @Test
  void aBatchThatWouldTakeAnyVariantNegativeIsRejectedWhole() {
    decrement(
            """
            {"items": [
              {"variantId": "TEST-REJECT-A", "quantity": 2},
              {"variantId": "TEST-REJECT-B", "quantity": 2}
            ]}
            """)
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(409)
        .jsonPath("$.insufficientStock[0]")
        .isEqualTo("TEST-REJECT-B")
        .jsonPath("$.insufficientStock.length()")
        .isEqualTo(1);

    assertThat(quantityOf("TEST-REJECT-A")).isEqualTo(10);
    assertThat(quantityOf("TEST-REJECT-B")).isEqualTo(1);
  }

  @Test
  void stockCanBeTakenExactlyToZeroButNoFurther() {
    decrement(one("TEST-EXACT", 3)).expectStatus().isOk();
    decrement(one("TEST-EXACT", 1)).expectStatus().isEqualTo(409);

    assertThat(quantityOf("TEST-EXACT")).isZero();
  }

  @Test
  void aDecrementCannotTakeReservedStock() {
    var variant = newVariant(5);
    reserved(item(variant, 3));

    decrement(one(variant, 3)).expectStatus().isEqualTo(409);
    decrement(one(variant, 2))
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].quantity")
        .isEqualTo(0)
        .jsonPath("$[0].onHand")
        .isEqualTo(3);

    assertThat(stockOf(variant)).isEqualTo(new StockView(variant, 0, 3, 3));
  }

  @Test
  void aVariantListedTwiceIsDecrementedByTheTotal() {
    decrement(
            """
            {"items": [
              {"variantId": "TEST-DUPLICATE", "quantity": 3},
              {"variantId": "TEST-DUPLICATE", "quantity": 2}
            ]}
            """)
        .expectStatus()
        .isEqualTo(409);
    assertThat(quantityOf("TEST-DUPLICATE")).isEqualTo(4);

    decrement(
            """
            {"items": [
              {"variantId": "TEST-DUPLICATE", "quantity": 1},
              {"variantId": "TEST-DUPLICATE", "quantity": 2}
            ]}
            """)
        .expectStatus()
        .isOk();
    assertThat(quantityOf("TEST-DUPLICATE")).isEqualTo(1);
  }

  @Test
  void aBatchWithAnUnknownVariantIsRejectedWhole() {
    decrement(
            """
            {"items": [
              {"variantId": "TEST-UNKNOWN-MIX", "quantity": 1},
              {"variantId": "NO-SUCH-VARIANT", "quantity": 1}
            ]}
            """)
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.unknownVariants[0]")
        .isEqualTo("NO-SUCH-VARIANT");

    assertThat(quantityOf("TEST-UNKNOWN-MIX")).isEqualTo(6);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {"items": []}
        """,
        """
        {}
        """,
        """
        {"items": [{"variantId": "TEST-INVALID", "quantity": 0}]}
        """,
        """
        {"items": [{"variantId": "TEST-INVALID", "quantity": -2}]}
        """,
        """
        {"items": [{"variantId": "TEST-INVALID"}]}
        """,
        """
        {"items": [{"quantity": 1}]}
        """,
        """
        {"items": [{"variantId": " ", "quantity": 1}]}
        """,
        """
        {"items": [{"variantId": "TEST-INVALID", "quantity": 2000000000},
                   {"variantId": "TEST-INVALID", "quantity": 2000000000}]}
        """,
        "not json"
      })
  void anInvalidBatchIsABadRequest(String body) {
    decrement(body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(quantityOf("TEST-INVALID")).isEqualTo(7);
  }

  @Test
  void decrementingNeedsAToken() {
    http.post()
        .uri("/stock/decrement")
        .contentType(MediaType.APPLICATION_JSON)
        .body(one("TEST-AUTH", 1))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(quantityOf("TEST-AUTH")).isEqualTo(8);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CUSTOMER", "STAFF"})
  void onlyCheckoutCanDecrement(String role) {
    http.post()
        .uri("/stock/decrement")
        .headers(h -> h.setBearerAuth(FakeKeycloak.token("someone-" + role, role)))
        .contentType(MediaType.APPLICATION_JSON)
        .body(one("TEST-AUTH", 1))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    assertThat(quantityOf("TEST-AUTH")).isEqualTo(8);
  }

  private static String one(String variantId, int quantity) {
    return """
        {"items": [{"variantId": "%s", "quantity": %d}]}
        """
        .formatted(variantId, quantity);
  }

  private RestTestClient.ResponseSpec decrement(String body) {
    return http.post()
        .uri("/stock/decrement")
        .headers(h -> h.setBearerAuth(checkoutToken()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }
}
