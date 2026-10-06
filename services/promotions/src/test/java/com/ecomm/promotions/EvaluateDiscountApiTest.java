package com.ecomm.promotions;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Checkout asks for every Discount a Checkout Session's lines are due: the running Campaigns', then
 * the Coupon's if it names one. When the Coupon doesn't apply, the 422 says why in its {@code
 * reason}.
 *
 * <p>Every test class shares one database, and others leave Campaigns running from 2020 to 2100. So
 * each test here runs in a year of its own, far past them: it sets the clock there, and its
 * Campaigns and Coupons are valid only that year.
 */
class EvaluateDiscountApiTest extends PromotionsApiTest {

  private static final String PHONE =
      """
      {"variantId": "PHN-1-BLK", "sku": "PHN-1", "category": "phones", "quantity": 1,
       "unitPrice": {"amountMinor": 80000, "currency": "EUR"}}
      """;

  private static final String HEADPHONES =
      """
      {"variantId": "AUD-1", "sku": "AUD-1", "category": "audio", "quantity": 2,
       "unitPrice": {"amountMinor": 10000, "currency": "EUR"}}
      """;

  @Test
  void campaignsOnlyApplyByPriorityWithoutACoupon() {
    var year = inYear(2201);
    var audio = createdCampaign(campaign(year, "Audio 15", 2201_20, 15, "[\"audio\"]"));
    var everything = createdCampaign(campaign(year, "Everything 10", 2201_10, 10, "[]"));

    // 10% of 1,000.00 is 100.00; the audio lines are left 180.00, and 15% of that is 27.00.
    evaluateLines(lines(PHONE, HEADPHONES), null)
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"discounts": [
              {"source": "CAMPAIGN", "campaignId": "%s", "campaignName": "Everything 10",
               "amount": {"amountMinor": 10000, "currency": "EUR"}},
              {"source": "CAMPAIGN", "campaignId": "%s", "campaignName": "Audio 15",
               "amount": {"amountMinor": 2700, "currency": "EUR"}}]}
            """
                .formatted(everything, audio),
            JsonCompareMode.STRICT);
  }

  @Test
  void aCouponOnlyAppliesUnderItsUpperCaseCode() {
    var year = inYear(2202);
    created(coupon(year, "EVAL-15", 15, null));

    // 15% of 10.01 EUR is 1.5015 EUR, rounded down.
    evaluateLines(
            lines(
                """
                {"variantId": "ODD-1", "sku": "ODD-1", "category": "phones", "quantity": 1,
                 "unitPrice": {"amountMinor": 1001, "currency": "EUR"}}
                """),
            "eval-15")
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"discounts": [{"source": "COUPON", "couponCode": "EVAL-15",
                            "amount": {"amountMinor": 150, "currency": "EUR"}}]}
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void campaignsAndACouponStackWithTheCouponLast() {
    var year = inYear(2203);
    var audioWeek = createdCampaign(campaign(year, "Audio week", 2203_10, 15, "[\"audio\"]"));
    created(coupon(year, "WELCOME-2203", 10, null));

    // 15% of the 200.00 headphones is 30.00; 10% of the 970.00 left is 97.00.
    evaluateLines(lines(PHONE, HEADPHONES), "WELCOME-2203")
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"discounts": [
              {"source": "CAMPAIGN", "campaignId": "%s", "campaignName": "Audio week",
               "amount": {"amountMinor": 3000, "currency": "EUR"}},
              {"source": "COUPON", "couponCode": "WELCOME-2203",
               "amount": {"amountMinor": 9700, "currency": "EUR"}}]}
            """
                .formatted(audioWeek),
            JsonCompareMode.STRICT);
  }

  @Test
  void nothingRunningMeansNoDiscounts() {
    inYear(2204);

    evaluateLines(lines(PHONE), null)
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"discounts": []}
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void aCampaignWhoseMinimumItsLinesDontReachIsLeftOut() {
    var year = inYear(2205);
    createdCampaign(
        """
        {"name": "Big audio", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "categories": ["audio"], "minimumSubtotal": {"amountMinor": 50000, "currency": "EUR"},
         "validFrom": "%s", "validUntil": "%s", "active": true, "priority": 220510}
        """
            .formatted(year.from(), year.until()));

    // The session comes to 1,000.00, but its audio lines only to 200.00.
    evaluateLines(lines(PHONE, HEADPHONES), null)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.discounts")
        .isEmpty();
  }

  @Test
  void aCouponThatDoesntApplyIsRejectedEvenWithCampaigns() {
    var year = inYear(2206);
    createdCampaign(campaign(year, "Half off", 2206_10, 50, "[]"));
    created(coupon(year, "OVER900-2206", 10, 90000L));

    // The phone's 800.00 is under the Coupon's 900.00, whatever the Campaign gives.
    expectRejected(evaluateLines(lines(PHONE), "OVER900-2206"), "belowMinimum");
  }

  @Test
  void aCouponsMinimumIsMeasuredBeforeTheCampaigns() {
    var year = inYear(2211);
    createdCampaign(campaign(year, "Half off", 2211_10, 50, "[]"));
    created(coupon(year, "OVER500-2211", 10, 50000L));

    // The Campaign leaves 400.00 of the 800.00 phone; the Coupon's 500.00 minimum is met on the
    // 800.00, and it takes 10% of the 400.00.
    evaluateLines(lines(PHONE), "OVER500-2211")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.discounts[1].amount.amountMinor")
        .isEqualTo(4000);
  }

  @Test
  void anUnknownCodeIsUnknown() {
    inYear(2207);
    expectRejected(evaluateLines(lines(PHONE), "NO-SUCH-COUPON"), "unknown");
  }

  @Test
  void anInactiveCouponIsInactive() {
    var year = inYear(2208);
    created(coupon(year, "EVAL-OFF", 10, null).replace("\"active\": true", "\"active\": false"));

    expectRejected(evaluateLines(lines(PHONE), "EVAL-OFF"), "inactive");
  }

  @Test
  void aCouponBeforeOrAfterItsValidityIsNotYetValidOrExpired() {
    var year = inYear(2209);
    created(
        """
        {"code": "EVAL-LATER", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2299-01-01T00:00:00Z", "validUntil": "2300-01-01T00:00:00Z", "active": true}
        """);
    created(
        """
        {"code": "EVAL-GONE", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "%s", "active": true}
        """
            .formatted(year.from()));

    expectRejected(evaluateLines(lines(PHONE), "EVAL-LATER"), "notYetValid");
    expectRejected(evaluateLines(lines(PHONE), "EVAL-GONE"), "expired");
  }

  @Test
  void aFixedAmountOrMinimumInAnotherCurrencyIsACurrencyMismatch() {
    var year = inYear(2210);
    created(
        """
        {"code": "EVAL-USD", "discount": {"type": "AMOUNT_OFF",
         "amountOff": {"amountMinor": 500, "currency": "USD"}},
         "validFrom": "%s", "validUntil": "%s", "active": true}
        """
            .formatted(year.from(), year.until()));
    created(
        coupon(year, "EVAL-USDMIN", 10, null)
            .replace(
                "\"active\"",
                "\"minimumSubtotal\": {\"amountMinor\": 100, \"currency\": \"USD\"}, \"active\""));

    expectRejected(evaluateLines(lines(PHONE), "EVAL-USD"), "currencyMismatch");
    expectRejected(evaluateLines(lines(PHONE), "EVAL-USDMIN"), "currencyMismatch");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        """
        {"lines": []}
        """,
        """
        {"lines": [{"sku": "S", "category": "c", "quantity": 1,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"lines": [{"variantId": "V", "category": "c", "quantity": 1,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"lines": [{"variantId": "V", "sku": "S", "quantity": 1,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"lines": [{"variantId": "V", "sku": "S", "category": "c", "quantity": 0,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"lines": [{"variantId": "V", "sku": "S", "category": "c", "quantity": 1.5,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"lines": [{"variantId": "V", "sku": "S", "category": "c", "quantity": 1,
                    "unitPrice": {"amountMinor": -1, "currency": "EUR"}}]}
        """,
        """
        {"lines": [{"variantId": "V", "sku": "S", "category": "c", "quantity": 1,
                    "unitPrice": {"amountMinor": "100", "currency": "EUR"}}]}
        """,
        """
        {"lines": [{"variantId": "V", "sku": "S", "category": "c", "quantity": 1,
                    "unitPrice": {"amountMinor": 100, "currency": "XYZ"}}]}
        """,
        """
        {"lines": [{"variantId": "V", "sku": "S", "category": "c", "quantity": 1,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}},
                   {"variantId": "W", "sku": "S", "category": "c", "quantity": 1,
                    "unitPrice": {"amountMinor": 100, "currency": "USD"}}]}
        """,
        """
        {"lines": [{"variantId": "V", "sku": "S", "category": "c", "quantity": 1,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}], "couponCode": " "}
        """,
        """
        {"lines": [{"variantId": "V", "sku": "S", "category": "c", "quantity": 1,
                    "unitPrice": {"amountMinor": 100, "currency": "EUR"}}], "couponCode": 10}
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
  void aBadLineIsNamedByItsPlace() {
    evaluate(
            checkoutToken(),
            """
            {"lines": [%s, {"variantId": "V", "sku": "S", "category": "c", "quantity": 0,
                            "unitPrice": {"amountMinor": 100, "currency": "EUR"}}]}
            """
                .formatted(PHONE))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("lines[1].quantity");
  }

  @Test
  void onlyCheckoutMayEvaluate() {
    var body = lines(PHONE);

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

  /** A year of its own, from 1 January to the next, with the clock set to its middle. */
  private record Year(String from, String until) {}

  private Year inYear(int year) {
    time.set(Instant.parse(year + "-06-01T00:00:00Z"));
    return new Year(year + "-01-01T00:00:00Z", (year + 1) + "-01-01T00:00:00Z");
  }

  /** An active Campaign for {@code percent}% off its Categories, valid only in {@code year}. */
  private static String campaign(
      Year year, String name, int priority, int percent, String categories) {
    return """
        {"name": "%s", "discount": {"type": "PERCENT_OFF", "percentOff": %d},
         "categories": %s, "validFrom": "%s", "validUntil": "%s", "active": true, "priority": %d}
        """
        .formatted(name, percent, categories, year.from(), year.until(), priority);
  }

  /** An active Coupon for {@code percent}% off, valid only in {@code year}. */
  private static String coupon(Year year, String code, int percent, Long minimumMinor) {
    var minimum =
        minimumMinor == null
            ? ""
            : "\"minimumSubtotal\": {\"amountMinor\": %d, \"currency\": \"EUR\"},"
                .formatted(minimumMinor);
    return """
        {"code": "%s", "discount": {"type": "PERCENT_OFF", "percentOff": %d}, %s
         "validFrom": "%s", "validUntil": "%s", "active": true}
        """
        .formatted(code, percent, minimum, year.from(), year.until());
  }

  private static String lines(String... lines) {
    return "{\"lines\": [" + String.join(",", lines) + "]}";
  }

  private RestTestClient.ResponseSpec evaluateLines(String linesBody, String couponCode) {
    var body =
        couponCode == null
            ? linesBody
            : linesBody.substring(0, linesBody.length() - 1)
                + ", \"couponCode\": \""
                + couponCode
                + "\"}";
    return evaluate(checkoutToken(), body);
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
}
