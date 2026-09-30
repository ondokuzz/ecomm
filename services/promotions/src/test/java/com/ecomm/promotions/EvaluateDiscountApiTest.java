package com.ecomm.promotions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Checkout asks what a Coupon takes off a subtotal. When the Coupon doesn't apply, the 422 says why
 * in its {@code reason}.
 */
class EvaluateDiscountApiTest extends PromotionsApiTest {

  @Test
  void aCouponThatAppliesGivesItsDiscountUnderItsUpperCaseCode() {
    created(percentOff("EVAL-15", 15));

    // 15% of 10.01 EUR is 1.5015 EUR, rounded down.
    evaluate("eval-15", 1001, "EUR")
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"couponCode": "EVAL-15", "discount": {"amountMinor": 150, "currency": "EUR"}}
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void aFixedAmountIsCappedAtTheSubtotal() {
    created(amountOff("EVAL-FIVER", 500, "EUR", null));

    evaluate("EVAL-FIVER", 399, "EUR")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.discount.amountMinor")
        .isEqualTo(399);
  }

  @Test
  void anUnknownCodeIsUnknown() {
    expectRejected(evaluate("NO-SUCH-COUPON", 159800, "EUR"), "unknown");
  }

  @Test
  void anInactiveCouponIsInactive() {
    created(
        """
        {"code": "EVAL-OFF", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": false}
        """);

    expectRejected(evaluate("EVAL-OFF", 159800, "EUR"), "inactive");
  }

  @Test
  void aCouponBeforeItsValidityIsNotYetValid() {
    created(
        """
        {"code": "EVAL-LATER", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2099-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """);

    expectRejected(evaluate("EVAL-LATER", 159800, "EUR"), "notYetValid");
  }

  @Test
  void aCouponAfterItsValidityIsExpired() {
    created(
        """
        {"code": "EVAL-GONE", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2021-01-01T00:00:00Z", "active": true}
        """);

    expectRejected(evaluate("EVAL-GONE", 159800, "EUR"), "expired");
  }

  @Test
  void aSubtotalBelowTheMinimumIsBelowMinimum() {
    created(amountOff("EVAL-MIN", 500, "EUR", 10000L));

    expectRejected(evaluate("EVAL-MIN", 9999, "EUR"), "belowMinimum");
    evaluate("EVAL-MIN", 10000, "EUR")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.discount.amountMinor")
        .isEqualTo(500);
  }

  @Test
  void aFixedAmountInAnotherCurrencyIsACurrencyMismatch() {
    created(amountOff("EVAL-EUR", 500, "EUR", null));

    expectRejected(evaluate("EVAL-EUR", 159800, "USD"), "currencyMismatch");
  }

  @Test
  void aMinimumInAnotherCurrencyIsACurrencyMismatch() {
    created(
        """
        {"code": "EVAL-EURMIN", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "minimumSubtotal": {"amountMinor": 100, "currency": "EUR"},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """);

    expectRejected(evaluate("EVAL-EURMIN", 159800, "USD"), "currencyMismatch");
  }

  @Test
  void anInactiveCouponIsInactiveBeforeAnythingElse() {
    created(
        """
        {"code": "EVAL-ALLWRONG", "discount": {"type": "AMOUNT_OFF",
         "amountOff": {"amountMinor": 500, "currency": "EUR"}},
         "minimumSubtotal": {"amountMinor": 10000, "currency": "EUR"},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2021-01-01T00:00:00Z", "active": false}
        """);

    expectRejected(evaluate("EVAL-ALLWRONG", 1, "USD"), "inactive");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {"subtotal": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"couponCode": " ", "subtotal": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"couponCode": 10, "subtotal": {"amountMinor": 100, "currency": "EUR"}}
        """,
        """
        {"couponCode": "WELCOME10"}
        """,
        """
        {"couponCode": "WELCOME10", "subtotal": {"amountMinor": -1, "currency": "EUR"}}
        """,
        """
        {"couponCode": "WELCOME10", "subtotal": {"amountMinor": 1.5, "currency": "EUR"}}
        """,
        """
        {"couponCode": "WELCOME10", "subtotal": {"amountMinor": "100", "currency": "EUR"}}
        """,
        """
        {"couponCode": "WELCOME10", "subtotal": {"amountMinor": 100, "currency": "XYZ"}}
        """,
        "not json"
      })
  void anInvalidRequestIsABadRequest(String body) {
    evaluate(checkoutToken(), body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void onlyCheckoutMayEvaluate() {
    var body =
        """
        {"couponCode": "WELCOME10", "subtotal": {"amountMinor": 100, "currency": "EUR"}}
        """;

    evaluate(customerToken(), body)
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    evaluate(staffToken(), body).expectStatus().isForbidden();
    http.post()
        .uri("/discounts/evaluate")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  private static void expectRejected(RestTestClient.ResponseSpec response, String reason) {
    response
        .expectStatus()
        .isEqualTo(422)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo(reason);
  }

  /** An active Coupon for a fixed amount off, valid from 2020 until 2100. */
  private static String amountOff(
      String code, long amountMinor, String currency, Long minimumMinor) {
    var minimum =
        minimumMinor == null
            ? ""
            : """
              "minimumSubtotal": {"amountMinor": %d, "currency": "%s"},
              """
                .formatted(minimumMinor, currency);
    return """
        {"code": "%s", "discount": {"type": "AMOUNT_OFF",
         "amountOff": {"amountMinor": %d, "currency": "%s"}}, %s
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true}
        """
        .formatted(code, amountMinor, currency, minimum);
  }
}
