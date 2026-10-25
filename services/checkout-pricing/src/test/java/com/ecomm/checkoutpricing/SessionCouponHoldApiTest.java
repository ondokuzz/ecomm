package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.checkoutpricing.domain.Discount;
import com.ecomm.commons.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Applying a Coupon holds one of its uses for the Checkout Session in Promotions, until the session
 * expires; removing or replacing it, or starting a new session, releases it. A Coupon whose Usage
 * limits are reached is refused at once, as a 422 with Promotions' reason.
 */
class SessionCouponHoldApiTest extends CheckoutApiTest {

  @Test
  void applyingACouponHoldsItForTheSessionUntilItExpires() {
    stubSuccessfulCheckout();
    stubDiscount("WELCOME10", 15980);
    var session = startSession().expectBody(SessionView.class).returnResult().getResponseBody();

    applyCoupon(session.id(), "welcome10").expectStatus().isOk();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(HOLDS_PATH))
            .withHeader("Authorization", equalTo("Bearer checkout-token-1"))
            .withRequestBody(
                equalToJson(
                    """
                    {"sessionId": "%s", "customerId": "%s", "couponCode": "WELCOME10",
                     "expiresAt": "%s"}
                    """
                        .formatted(session.id(), CUSTOMER_ID, session.expiresAt()))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"exhausted", "alreadyRedeemed"})
  void aCouponThatCantBeHeldIsUnprocessableAndLeavesTheSessionAsItWas(String reason) {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    stubDiscount("FIVER", 500);
    applyCoupon(sessionId, "FIVER").expectStatus().isOk();
    stubDiscount("FIRST100", 15980);
    stubRefusedHold(reason);

    applyCoupon(sessionId, "FIRST100")
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo(reason);

    currentSession()
        .expectBody()
        .jsonPath("$.discounts[0].couponCode")
        .isEqualTo("FIVER")
        .jsonPath("$.total.amountMinor")
        .isEqualTo(159300);
  }

  @Test
  void aCouponThatDoesntApplyIsNeverHeld() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    stubRejectedCoupon("expired");

    applyCoupon(sessionId, "OLD").expectStatus().isEqualTo(422);

    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo(HOLDS_PATH)));
  }

  @Test
  void anotherCouponIsHeldForTheSameSessionInPlaceOfTheFirst() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    stubDiscount("WELCOME10", 15980);
    applyCoupon(sessionId, "WELCOME10").expectStatus().isOk();
    stubDiscount("FIVER", 500);

    applyCoupon(sessionId, "FIVER").expectStatus().isOk();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(HOLDS_PATH))
            .withRequestBody(
                equalToJson(
                    "{\"sessionId\": \"%s\", \"couponCode\": \"FIVER\"}".formatted(sessionId),
                    true,
                    true)));
  }

  @Test
  void removingTheCouponReleasesItsHold() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    stubDiscount("WELCOME10", 15980);
    applyCoupon(sessionId, "WELCOME10").expectStatus().isOk();

    removeCoupon(sessionId).expectStatus().isOk();

    DOWNSTREAM.verify(deleteRequestedFor(urlEqualTo(HOLDS_PATH + "/" + sessionId)));
  }

  @Test
  void removingWhenThereIsNoCouponReleasesNothing() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    removeCoupon(sessionId).expectStatus().isOk();

    DOWNSTREAM.verify(0, deleteRequestedFor(urlPathMatching(HOLDS_PATH + "/.*")));
  }

  @Test
  void startingANewSessionReleasesTheOldOnesHold() {
    stubSuccessfulCheckout();
    var first = startedSessionId();
    stubDiscount("WELCOME10", 15980);
    applyCoupon(first, "WELCOME10").expectStatus().isOk();

    var second = startedSessionId();

    assertThat(second).isNotEqualTo(first);
    DOWNSTREAM.verify(deleteRequestedFor(urlEqualTo(HOLDS_PATH + "/" + first)));
  }

  @Test
  void promotionsFailingToHoldIsABadGatewayAndChangesNothing() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    stubDiscount("WELCOME10", 15980);
    stubHold(aResponse().withStatus(500));

    applyCoupon(sessionId, "WELCOME10").expectStatus().isEqualTo(502);

    currentSession().expectBody().jsonPath("$.discounts").isEmpty();
  }

  @Test
  void promotionsFailingToReleaseIsABadGatewayAndKeepsTheCoupon() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    stubDiscount("WELCOME10", 15980);
    applyCoupon(sessionId, "WELCOME10").expectStatus().isOk();
    DOWNSTREAM.stubFor(
        delete(urlPathMatching(HOLDS_PATH + "/[^/]+"))
            .atPriority(1)
            .willReturn(aResponse().withStatus(503)));

    removeCoupon(sessionId).expectStatus().isEqualTo(502);

    currentSession().expectBody().jsonPath("$.discounts[0].couponCode").isEqualTo("WELCOME10");
  }

  @Test
  void aCampaignStartingMidSessionIsntAddedWhenItIsPaid() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    stubDiscount("WELCOME10", 15980);
    applyCoupon(sessionId, "WELCOME10").expectStatus().isOk();
    ship(sessionId);
    // A Campaign starts, and the Coupon expires, before the Customer pays.
    stubCampaigns(campaignDiscount("c-late", "Late", 10000));
    stubRejectedCoupon("expired");

    pay(sessionId).expectStatus().isOk();

    var paid = saga.started().getFirst().session();
    assertThat(paid.discounts()).containsExactly(Discount.coupon("WELCOME10", money(15980)));
    DOWNSTREAM.verify(
        1,
        postRequestedFor(urlEqualTo(EVALUATE_PATH))
            .withRequestBody(matchingJsonPath("$.couponCode")));
  }

  @Test
  void aCampaignThatEndsOrIsSwitchedOffMidSessionIsStillHonouredWhenItIsPaid() {
    stubSuccessfulCheckout();
    stubCampaigns(campaignDiscount("c-ending", "Ending", 10000));
    var sessionId = startedSessionId();
    ship(sessionId);
    // The Campaign is over, or switched off, by the time the Customer pays.
    stubCampaigns();

    pay(sessionId).expectStatus().isOk();

    assertThat(saga.started().getFirst().session().discounts())
        .containsExactly(Discount.campaign("c-ending", "Ending", money(10000)));
  }

  private static Money money(long minor) {
    return Money.of(minor, "EUR");
  }
}
