package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.commons.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * A Checkout Session holds every Discount Promotions says it is due: the running Campaigns' from
 * when it starts, and the Coupon's on top once one is applied. Each line goes to Promotions with
 * its Product's SKU and Category, and every Coupon change asks again about the Campaigns too.
 */
class SessionDiscountsApiTest extends CheckoutApiTest {

  private static final String AUDIO_WEEK = "0c5e0a1d-0000-4000-8000-000000000001";

  /** Two Pixel 9s (1598.00) and a pair of AirPods (249.00 EUR): 1847.00 in all. */
  private void stubPhonesAndHeadphones() {
    stubSuccessfulCheckout();
    stubCart(
        """
        {"variantId": "PHN-PIXEL-9", "quantity": 2},
        {"variantId": "AUD-AIRPODS-WHT", "quantity": 1}
        """);
    stubVariant("AUD-AIRPODS-WHT", "AUD-AIRPODS", "audio", 24900);
  }

  @Test
  void startingASessionSendsEachLinesSkuAndCategoryAndNoCoupon() {
    stubPhonesAndHeadphones();

    startSession().expectStatus().isCreated();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(EVALUATE_PATH))
            .withRequestBody(
                equalToJson(
                    """
                    {"lines": [
                      {"variantId": "PHN-PIXEL-9", "sku": "PHN-PIXEL-9", "category": "phones",
                       "quantity": 2, "unitPrice": {"amountMinor": 79900, "currency": "EUR"}},
                      {"variantId": "AUD-AIRPODS-WHT", "sku": "AUD-AIRPODS", "category": "audio",
                       "quantity": 1, "unitPrice": {"amountMinor": 24900, "currency": "EUR"}}]}
                    """)));
  }

  @Test
  void aNewSessionHoldsTheRunningCampaignsDiscounts() {
    stubPhonesAndHeadphones();
    stubCampaigns(campaignDiscount(AUDIO_WEEK, "Audio week", 3735));

    // 1847.00 less 37.35.
    startSession()
        .expectStatus()
        .isCreated()
        .expectBody()
        .json(
            """
            {"subtotal": {"amountMinor": 184700, "currency": "EUR"},
             "discounts": [{"source": "CAMPAIGN", "campaignId": "%s", "campaignName": "Audio week",
                            "amount": {"amountMinor": 3735, "currency": "EUR"}}],
             "tax": {"amountMinor": 0, "currency": "EUR"},
             "total": {"amountMinor": 180965, "currency": "EUR"}}
            """
                .formatted(AUDIO_WEEK));
  }

  @Test
  void applyingACouponHoldsEveryDiscountPromotionsReturns() {
    stubPhonesAndHeadphones();
    stubCampaigns(campaignDiscount(AUDIO_WEEK, "Audio week", 3735));
    var sessionId = startedSessionId();
    stubCouponEvaluation(
        campaignDiscount(AUDIO_WEEK, "Audio week", 3735), couponDiscount("WELCOME10", 18096));

    // 1847.00 less 37.35, less 180.96.
    applyCoupon(sessionId, "welcome10")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.discounts.length()")
        .isEqualTo(2)
        .jsonPath("$.discounts[0].campaignName")
        .isEqualTo("Audio week")
        .jsonPath("$.discounts[1].couponCode")
        .isEqualTo("WELCOME10")
        .jsonPath("$.discounts[1].amount.amountMinor")
        .isEqualTo(18096)
        .jsonPath("$.total.amountMinor")
        .isEqualTo(162869);
  }

  @Test
  void aCouponChangeAsksAboutTheCampaignsAgain() {
    stubPhonesAndHeadphones();
    var sessionId = startedSessionId();
    // Audio week starts while the Customer is checking out.
    stubCouponEvaluation(
        campaignDiscount(AUDIO_WEEK, "Audio week", 3735), couponDiscount("WELCOME10", 18096));
    stubCampaigns(campaignDiscount(AUDIO_WEEK, "Audio week", 3735));

    applyCoupon(sessionId, "WELCOME10")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.discounts[0].campaignName")
        .isEqualTo("Audio week");

    removeCoupon(sessionId)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.discounts.length()")
        .isEqualTo(1)
        .jsonPath("$.discounts[0].campaignName")
        .isEqualTo("Audio week")
        .jsonPath("$.total.amountMinor")
        .isEqualTo(180965);
  }

  @Test
  void aRejectedCouponKeepsTheSessionsDiscounts() {
    stubPhonesAndHeadphones();
    stubCampaigns(campaignDiscount(AUDIO_WEEK, "Audio week", 3735));
    var sessionId = startedSessionId();
    stubRejectedCoupon("expired");

    applyCoupon(sessionId, "OLDCODE").expectStatus().isEqualTo(422);

    currentSession()
        .expectBody()
        .jsonPath("$.discounts.length()")
        .isEqualTo(1)
        .jsonPath("$.total.amountMinor")
        .isEqualTo(180965);
  }

  @Test
  void theSessionIsPaidWithEveryDiscountInTheOrderTheyApply() {
    stubPhonesAndHeadphones();
    stubCampaigns(campaignDiscount(AUDIO_WEEK, "Audio week", 3735));
    stubCouponEvaluation(
        campaignDiscount(AUDIO_WEEK, "Audio week", 3735), couponDiscount("WELCOME10", 18096));
    var sessionId = startedSessionId();
    applyCoupon(sessionId, "WELCOME10").expectStatus().isOk();

    pay(sessionId).expectStatus().isOk();

    assertThat(saga.started().getFirst().session().discounts())
        .containsExactly(
            Discount.campaign(AUDIO_WEEK, "Audio week", Money.of(3735, "EUR")),
            Discount.coupon("WELCOME10", Money.of(18096, "EUR")));
  }

  @Test
  void aSessionWithoutDiscountsIsPaidWithNone() {
    stubSuccessfulCheckout();

    checkout().expectStatus().isOk();

    assertThat(saga.started().getFirst().session().discounts()).isEmpty();
  }

  @Test
  void promotionsFailingWhenASessionStartsIsABadGatewayAndHoldsNoStock() {
    stubSuccessfulCheckout();
    stubEvaluation(aResponse().withStatus(500));

    startSession()
        .expectStatus()
        .isEqualTo(502)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo("/reservations")));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // More off than the session comes to.
        """
        {"discounts": [{"source": "CAMPAIGN", "campaignId": "c", "campaignName": "Too much",
                        "amount": {"amountMinor": 159801, "currency": "EUR"}}]}
        """,
        // Another currency.
        """
        {"discounts": [{"source": "CAMPAIGN", "campaignId": "c", "campaignName": "Dollars",
                        "amount": {"amountMinor": 100, "currency": "USD"}}]}
        """,
        // A negative amount.
        """
        {"discounts": [{"source": "CAMPAIGN", "campaignId": "c", "campaignName": "Less",
                        "amount": {"amountMinor": -1, "currency": "EUR"}}]}
        """,
        // A Coupon's Discount when none was asked for.
        """
        {"discounts": [{"source": "COUPON", "couponCode": "WHO",
                        "amount": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        // A Campaign's without its name, or an unknown source.
        """
        {"discounts": [{"source": "CAMPAIGN", "campaignId": "c",
                        "amount": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        """
        {"discounts": [{"source": "GIFT", "amount": {"amountMinor": 100, "currency": "EUR"}}]}
        """,
        "{}"
      })
  void anAnswerPromotionsShouldNeverGiveIsABadGateway(String answer) {
    stubSuccessfulCheckout();
    stubCampaigns();
    stubEvaluation(okJson(answer));

    startSession().expectStatus().isEqualTo(502);
  }

  @Test
  void aCouponAnswerWithoutTheCouponLastIsABadGateway() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    stubCouponEvaluation(campaignDiscount(AUDIO_WEEK, "Audio week", 100));

    applyCoupon(sessionId, "WELCOME10").expectStatus().isEqualTo(502);
    currentSession().expectBody().jsonPath("$.discounts").isEmpty();
  }

  @Test
  void anUnknownVariantAsksPromotionsNothing() {
    stubSuccessfulCheckout();
    stubCart("{\"variantId\": \"GONE-1\", \"quantity\": 1}");
    DOWNSTREAM.resetRequests();

    startSession().expectStatus().isEqualTo(409);

    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo(EVALUATE_PATH)));
    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo("/reservations")));
  }
}
