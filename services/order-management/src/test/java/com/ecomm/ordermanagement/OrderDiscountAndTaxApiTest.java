package com.ecomm.ordermanagement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * An Order records the discount and tax Checkout sends with its lines, and its total is the lines,
 * less the discount, plus the tax: the amount its Payment is authorized for.
 */
class OrderDiscountAndTaxApiTest extends OrderApiTest {

  /** The two-line Order (1747.50 EUR) with {@code extra} fields added to its body. */
  static String twoLineOrderWith(String extra) {
    return """
        {"customerId": "%s", "lines": [
          {"variantId": "PHN-PIXEL-9", "quantity": 2,
           "unitPrice": {"amountMinor": 79900, "currency": "EUR"}},
          {"variantId": "AUD-AIRPODS-PRO-2", "quantity": 1,
           "unitPrice": {"amountMinor": 14950, "currency": "EUR"}}
        ], %s}
        """
        .formatted(CUSTOMER, extra);
  }

  OrderView placedWith(String extra) {
    return place(twoLineOrderWith(extra))
        .expectStatus()
        .isCreated()
        .expectBody(OrderView.class)
        .returnResult()
        .getResponseBody();
  }

  OrderView readBack(String id) {
    return http.get()
        .uri("/orders/{id}", id)
        .headers(h -> h.setBearerAuth(customerToken()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(OrderView.class)
        .returnResult()
        .getResponseBody();
  }

  @Test
  void theDiscountAndTaxAreStoredAndReturned() {
    var order =
        placedWith(
            """
            "discount": {"couponCode": "WELCOME10", "amount": {"amountMinor": 17475, "currency": "EUR"}},
            "tax": {"amountMinor": 31455, "currency": "EUR"}
            """);

    var expectedDiscount = new DiscountView("WELCOME10", new AmountView(17475, "EUR"));
    assertThat(order.discount()).isEqualTo(expectedDiscount);
    assertThat(order.tax()).isEqualTo(new AmountView(31455, "EUR"));
    assertThat(readBack(order.id())).isEqualTo(order);
  }

  @Test
  void theTotalIsTheLinesLessTheDiscountPlusTheTax() {
    var order =
        placedWith(
            """
            "discount": {"couponCode": "WELCOME10", "amount": {"amountMinor": 17475, "currency": "EUR"}},
            "tax": {"amountMinor": 31455, "currency": "EUR"}
            """);

    assertThat(order.subtotal()).isEqualTo(new AmountView(174750, "EUR"));
    // 1747.50 − 174.75 + 314.55
    assertThat(order.total()).isEqualTo(new AmountView(188730, "EUR"));
  }

  @Test
  void withoutADiscountTheTotalIsTheLinesPlusTheTax() {
    var order = placedWith("\"tax\": {\"amountMinor\": 34950, \"currency\": \"EUR\"}");

    assertThat(order.discount()).isNull();
    assertThat(order.subtotal()).isEqualTo(new AmountView(174750, "EUR"));
    assertThat(order.total()).isEqualTo(new AmountView(209700, "EUR"));
  }

  @Test
  void aDiscountCanBringTheTotalToZero() {
    var order =
        placedWith(
            """
            "discount": {"couponCode": "ALLFREE", "amount": {"amountMinor": 184750, "currency": "EUR"}},
            "tax": {"amountMinor": 10000, "currency": "EUR"}
            """);

    assertThat(order.total()).isEqualTo(new AmountView(0, "EUR"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // No tax: Checkout always states it, even when it is zero.
        "\"discount\": null",
        "\"tax\": null",
        "\"tax\": {\"amountMinor\": -1, \"currency\": \"EUR\"}",
        "\"tax\": {\"amountMinor\": 1.5, \"currency\": \"EUR\"}",
        "\"tax\": {\"amountMinor\": 100}",
        "\"tax\": 100",
        // A discount larger than the lines and tax together: the total would be negative.
        """
        "discount": {"couponCode": "TOOMUCH", "amount": {"amountMinor": 184751, "currency": "EUR"}},
        "tax": {"amountMinor": 10000, "currency": "EUR"}
        """,
        """
        "discount": {"couponCode": "NEGATIVE", "amount": {"amountMinor": -1, "currency": "EUR"}},
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discount": {"amount": {"amountMinor": 100, "currency": "EUR"}},
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discount": {"couponCode": " ", "amount": {"amountMinor": 100, "currency": "EUR"}},
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discount": {"couponCode": 10, "amount": {"amountMinor": 100, "currency": "EUR"}},
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discount": {"couponCode": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                     "amount": {"amountMinor": 100, "currency": "EUR"}},
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discount": {"couponCode": "WELCOME10"},
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        // Currency mismatches: the discount and tax are in the Order's currency.
        "\"tax\": {\"amountMinor\": 0, \"currency\": \"USD\"}",
        """
        "discount": {"couponCode": "WELCOME10", "amount": {"amountMinor": 100, "currency": "USD"}},
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        // A total too large to hold.
        "\"tax\": {\"amountMinor\": 9223372036854775807, \"currency\": \"EUR\"}"
      })
  void anInvalidDiscountOrTaxIsABadRequest(String extra) {
    place(twoLineOrderWith(extra))
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void anOrderPlacedBeforeDiscountsAndTaxReadsBackWithNoDiscountAndZeroTax() {
    // Placed by a Sprint 1 stack, before this migration (db/testdata/V1_1__sprint1_order.sql).
    var order = readBack("51000000-0000-4000-8000-000000000001");

    assertThat(order.discount()).isNull();
    assertThat(order.tax()).isEqualTo(new AmountView(0, "EUR"));
    assertThat(order.subtotal()).isEqualTo(new AmountView(79900, "EUR"));
    assertThat(order.total()).isEqualTo(new AmountView(79900, "EUR"));
  }
}
