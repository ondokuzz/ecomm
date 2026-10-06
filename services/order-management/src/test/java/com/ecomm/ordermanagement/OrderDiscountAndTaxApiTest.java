package com.ecomm.ordermanagement;

import static com.ecomm.ordermanagement.OrderApiTest.DiscountView.campaign;
import static com.ecomm.ordermanagement.OrderApiTest.DiscountView.coupon;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * An Order records every Discount and the tax Checkout sends with its lines, and its total is the
 * lines, less the Discounts, plus the tax: the amount its Payment is authorized for.
 */
class OrderDiscountAndTaxApiTest extends OrderApiTest {

  private static final String CAMPAIGN_ID = "7f1c2b9e-0000-4000-8000-000000000001";

  /** Audio week's Discount, then WELCOME10's, as Checkout sends them. */
  private static final String TWO_DISCOUNTS =
      """
      "discounts": [
        {"source": "CAMPAIGN", "campaignId": "%s", "campaignName": "Audio week",
         "amount": {"amountMinor": 2242, "currency": "EUR"}},
        {"source": "COUPON", "couponCode": "WELCOME10",
         "amount": {"amountMinor": 17251, "currency": "EUR"}}]
      """
          .formatted(CAMPAIGN_ID);

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
  void everyDiscountAndTheTaxAreStoredAndReturnedInOrder() {
    var order =
        placedWith(TWO_DISCOUNTS + ", \"tax\": {\"amountMinor\": 31455, \"currency\": \"EUR\"}");

    assertThat(order.discounts())
        .containsExactly(campaign(CAMPAIGN_ID, "Audio week", 2242), coupon("WELCOME10", 17251));
    assertThat(order.tax()).isEqualTo(new AmountView(31455, "EUR"));
    assertThat(readBack(order.id())).isEqualTo(order);
  }

  @Test
  void theTotalIsTheLinesLessEveryDiscountPlusTheTax() {
    var order =
        placedWith(TWO_DISCOUNTS + ", \"tax\": {\"amountMinor\": 31455, \"currency\": \"EUR\"}");

    assertThat(order.subtotal()).isEqualTo(new AmountView(174750, "EUR"));
    // 1747.50 − 22.42 − 172.51 + 314.55
    assertThat(order.total()).isEqualTo(new AmountView(186712, "EUR"));
  }

  @Test
  void withoutDiscountsTheTotalIsTheLinesPlusTheTax() {
    var order =
        placedWith("\"discounts\": [], \"tax\": {\"amountMinor\": 34950, \"currency\": \"EUR\"}");

    assertThat(order.discounts()).isEmpty();
    assertThat(order.total()).isEqualTo(new AmountView(209700, "EUR"));
  }

  @Test
  void leavingOutTheDiscountsMeansNone() {
    var order = placedWith("\"tax\": {\"amountMinor\": 34950, \"currency\": \"EUR\"}");

    assertThat(order.discounts()).isEmpty();
    assertThat(order.total()).isEqualTo(new AmountView(209700, "EUR"));
  }

  @Test
  void discountsCanBringTheTotalToZero() {
    var order =
        placedWith(
            """
            "discounts": [
              {"source": "CAMPAIGN", "campaignId": "c-1", "campaignName": "Half",
               "amount": {"amountMinor": 87375, "currency": "EUR"}},
              {"source": "COUPON", "couponCode": "ALLFREE",
               "amount": {"amountMinor": 87375, "currency": "EUR"}}],
            "tax": {"amountMinor": 0, "currency": "EUR"}
            """);

    assertThat(order.total()).isEqualTo(new AmountView(0, "EUR"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // No tax: Checkout always states it, even when it is zero.
        "\"discounts\": []",
        "\"tax\": null",
        "\"tax\": {\"amountMinor\": -1, \"currency\": \"EUR\"}",
        "\"tax\": {\"amountMinor\": 1.5, \"currency\": \"EUR\"}",
        "\"tax\": {\"amountMinor\": 100}",
        "\"tax\": 100",
        // Discounts larger than the lines and tax together: the total would be negative.
        """
        "discounts": [
          {"source": "COUPON", "couponCode": "MOST", "amount": {"amountMinor": 100000, "currency": "EUR"}},
          {"source": "CAMPAIGN", "campaignId": "c-1", "campaignName": "Rest",
           "amount": {"amountMinor": 84751, "currency": "EUR"}}],
        "tax": {"amountMinor": 10000, "currency": "EUR"}
        """,
        // The old single "discount" is gone.
        """
        "discount": {"couponCode": "WELCOME10", "amount": {"amountMinor": 100, "currency": "EUR"}},
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        "\"discounts\": {}, \"tax\": {\"amountMinor\": 0, \"currency\": \"EUR\"}",
        "\"discounts\": [null], \"tax\": {\"amountMinor\": 0, \"currency\": \"EUR\"}",
        // A source, and what it needs to name it.
        """
        "discounts": [{"couponCode": "WELCOME10", "amount": {"amountMinor": 100, "currency": "EUR"}}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discounts": [{"source": "GIFT", "amount": {"amountMinor": 100, "currency": "EUR"}}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discounts": [{"source": "COUPON", "amount": {"amountMinor": 100, "currency": "EUR"}}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discounts": [{"source": "COUPON", "couponCode": " ",
                       "amount": {"amountMinor": 100, "currency": "EUR"}}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discounts": [{"source": "COUPON", "couponCode": 10,
                       "amount": {"amountMinor": 100, "currency": "EUR"}}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discounts": [{"source": "COUPON",
                       "couponCode": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                       "amount": {"amountMinor": 100, "currency": "EUR"}}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discounts": [{"source": "COUPON", "couponCode": "WELCOME10", "campaignName": "Both",
                       "amount": {"amountMinor": 100, "currency": "EUR"}}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discounts": [{"source": "CAMPAIGN", "campaignName": "No ID",
                       "amount": {"amountMinor": 100, "currency": "EUR"}}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discounts": [{"source": "CAMPAIGN", "campaignId": "c-1",
                       "amount": {"amountMinor": 100, "currency": "EUR"}}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discounts": [{"source": "CAMPAIGN", "campaignId": "c-1", "campaignName": "Code too",
                       "couponCode": "WELCOME10", "amount": {"amountMinor": 100, "currency": "EUR"}}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        // An amount: present, not negative, in the Order's currency.
        """
        "discounts": [{"source": "COUPON", "couponCode": "WELCOME10"}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discounts": [{"source": "COUPON", "couponCode": "NEGATIVE",
                       "amount": {"amountMinor": -1, "currency": "EUR"}}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        """
        "discounts": [{"source": "COUPON", "couponCode": "WELCOME10",
                       "amount": {"amountMinor": 100, "currency": "USD"}}],
        "tax": {"amountMinor": 0, "currency": "EUR"}
        """,
        "\"tax\": {\"amountMinor\": 0, \"currency\": \"USD\"}",
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
  void anOrderPlacedBeforeDiscountsAndTaxReadsBackWithNoDiscountsAndZeroTax() {
    // Placed by a Sprint 1 stack, before Orders had Discounts
    // (db/testdata/V1_1__sprint1_order.sql).
    var order = readBack("51000000-0000-4000-8000-000000000001");

    assertThat(order.discounts()).isEmpty();
    assertThat(order.tax()).isEqualTo(new AmountView(0, "EUR"));
    assertThat(order.total()).isEqualTo(new AmountView(79900, "EUR"));
  }

  @Test
  void anOrderWithASprint2CouponKeepsItAsACouponDiscount() {
    // Placed by a Sprint 2 stack with its one Discount (db/testdata/V2_1__sprint2_orders.sql).
    var order =
        http.get()
            .uri("/orders/{id}", "5e000000-0000-4000-8000-000000000002")
            .headers(h -> h.setBearerAuth(tokenOf("customer-sprint2")))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(OrderView.class)
            .returnResult()
            .getResponseBody();

    assertThat(order.discounts()).containsExactly(coupon("WELCOME10", 7990));
    // 799.00 − 79.90 + 143.82
    assertThat(order.total()).isEqualTo(new AmountView(86292, "EUR"));
  }
}
