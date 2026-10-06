package com.ecomm.checkoutpricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.ecomm.commons.security.FakeKeycloak;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * A Customer applies a Coupon to their Checkout Session, and removes it. Promotions says what it
 * takes off, after any running Campaigns' Discounts (see {@link SessionDiscountsApiTest}); a Coupon
 * that doesn't apply is a 422 with Promotions' reason, and changes nothing.
 */
class SessionCouponApiTest extends CheckoutApiTest {

  @Test
  void applyingACouponShowsItsDiscountAndTakesItOffTheTotal() {
    stubSuccessfulCheckout();
    stubDiscount("WELCOME10", 15980);
    var sessionId = startedSessionId();

    // 2 × 799.00 = 1598.00, less 159.80.
    applyCoupon(sessionId, "welcome10")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(sessionId)
        .jsonPath("$.subtotal.amountMinor")
        .isEqualTo(159800)
        .jsonPath("$.discounts[0].source")
        .isEqualTo("COUPON")
        .jsonPath("$.discounts[0].couponCode")
        .isEqualTo("WELCOME10")
        .jsonPath("$.discounts[0].amount.amountMinor")
        .isEqualTo(15980)
        .jsonPath("$.discounts[0].amount.currency")
        .isEqualTo("EUR")
        .jsonPath("$.tax.amountMinor")
        .isEqualTo(0)
        .jsonPath("$.total.amountMinor")
        .isEqualTo(143820);

    currentSession()
        .expectBody()
        .jsonPath("$.discounts[0].couponCode")
        .isEqualTo("WELCOME10")
        .jsonPath("$.total.amountMinor")
        .isEqualTo(143820);
  }

  @Test
  void aNewSessionWithNoCampaignRunningHasNoDiscounts() {
    stubSuccessfulCheckout();

    startSession()
        .expectBody()
        .jsonPath("$.discounts")
        .isEmpty()
        .jsonPath("$.total.amountMinor")
        .isEqualTo(159800);
  }

  @Test
  void promotionsEvaluatesTheCodeOnTheSessionsLinesWithCheckoutsToken() {
    stubSuccessfulCheckout();
    stubDiscount("WELCOME10", 15980);

    applyCoupon(startedSessionId(), "welcome10").expectStatus().isOk();

    DOWNSTREAM.verify(
        postRequestedFor(urlEqualTo(EVALUATE_PATH))
            .withHeader("Authorization", equalTo("Bearer checkout-token-1"))
            .withRequestBody(
                equalToJson(
                    """
                    {"lines": [{"variantId": "PHN-PIXEL-9", "sku": "PHN-PIXEL-9",
                                "category": "phones", "quantity": 2,
                                "unitPrice": {"amountMinor": 79900, "currency": "EUR"}}],
                     "couponCode": "welcome10"}
                    """)));
  }

  @Test
  void anotherCouponReplacesTheFirst() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    stubDiscount("WELCOME10", 15980);
    applyCoupon(sessionId, "WELCOME10").expectStatus().isOk();
    stubDiscount("FIVER", 500);

    applyCoupon(sessionId, "FIVER")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.discounts[0].couponCode")
        .isEqualTo("FIVER")
        .jsonPath("$.total.amountMinor")
        .isEqualTo(159300);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "unknown",
        "inactive",
        "notYetValid",
        "expired",
        "belowMinimum",
        "currencyMismatch"
      })
  void aCouponThatDoesntApplyIsUnprocessableWithPromotionsReason(String reason) {
    stubSuccessfulCheckout();
    stubRejectedCoupon(reason);

    applyCoupon(startedSessionId(), "NOPE")
        .expectStatus()
        .isEqualTo(422)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.reason")
        .isEqualTo(reason)
        .jsonPath("$.correlationId")
        .isNotEmpty();
  }

  @Test
  void aCouponThatDoesntApplyLeavesTheSessionAsItWas() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    stubDiscount("WELCOME10", 15980);
    applyCoupon(sessionId, "WELCOME10").expectStatus().isOk();
    stubRejectedCoupon("expired");

    applyCoupon(sessionId, "OLDCODE").expectStatus().isEqualTo(422);

    currentSession()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.discounts[0].couponCode")
        .isEqualTo("WELCOME10")
        .jsonPath("$.total.amountMinor")
        .isEqualTo(143820);
  }

  @Test
  void removingTheCouponPutsTheTotalBack() {
    stubSuccessfulCheckout();
    stubDiscount("WELCOME10", 15980);
    var sessionId = startedSessionId();
    applyCoupon(sessionId, "WELCOME10").expectStatus().isOk();

    removeCoupon(sessionId)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(sessionId)
        .jsonPath("$.discounts")
        .isEmpty()
        .jsonPath("$.total.amountMinor")
        .isEqualTo(159800);

    currentSession().expectBody().jsonPath("$.discounts").isEmpty();
  }

  @Test
  void removingWhenThereIsNoCouponChangesNothing() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    removeCoupon(sessionId)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.total.amountMinor")
        .isEqualTo(159800);
  }

  @Test
  void theSessionKeepsItsExpiry() {
    stubSuccessfulCheckout();
    stubDiscount("WELCOME10", 15980);
    var session = startSession().expectBody(SessionView.class).returnResult().getResponseBody();
    clock.advance(Duration.ofMinutes(5));

    applyCoupon(session.id(), "WELCOME10")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.expiresAt")
        .isEqualTo(session.expiresAt().toString());

    clock.advance(Duration.ofMinutes(10));
    currentSession().expectStatus().isNotFound();
  }

  @Test
  void applyingToAnExpiredSessionIsGoneAndAsksNothing() {
    stubSuccessfulCheckout();
    stubDiscount("WELCOME10", 15980);
    var sessionId = startedSessionId();
    clock.advance(Duration.ofMinutes(15));
    DOWNSTREAM.resetRequests();

    applyCoupon(sessionId, "WELCOME10")
        .expectStatus()
        .isEqualTo(410)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    removeCoupon(sessionId).expectStatus().isEqualTo(410);

    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
  }

  @Test
  void anUnknownOrAnotherCustomersSessionIsNotFound() {
    stubSuccessfulCheckout();
    stubDiscount("WELCOME10", 15980);
    var sessionId = startedSessionId();
    DOWNSTREAM.resetRequests();
    var otherCustomer = FakeKeycloak.token("customer-7", "CUSTOMER");

    applyCoupon("0b7e6a52-0000-4000-8000-000000000009", "WELCOME10").expectStatus().isNotFound();
    applyCouponWith(otherCustomer, sessionId, "{\"code\": \"WELCOME10\"}")
        .expectStatus()
        .isNotFound();
    removeCoupon(otherCustomer, sessionId).expectStatus().isNotFound();

    DOWNSTREAM.verify(0, anyRequestedFor(anyUrl()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"code\": \" \"}",
        "{\"code\": 10}",
        "{\"code\": \"XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\"}",
        "not json"
      })
  void anInvalidCodeIsABadRequestAndAsksNothing(String body) {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();
    DOWNSTREAM.resetRequests();

    applyCouponWith(customerToken(), sessionId, body)
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    DOWNSTREAM.verify(0, postRequestedFor(urlEqualTo(EVALUATE_PATH)));
  }

  @Test
  void promotionsFailingIsABadGatewayAndChangesNothing() {
    stubSuccessfulCheckout();
    stubCouponEvaluation(aResponse().withStatus(500));
    var sessionId = startedSessionId();

    applyCoupon(sessionId, "WELCOME10")
        .expectStatus()
        .isEqualTo(502)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    currentSession().expectBody().jsonPath("$.discounts").isEmpty();
  }

  @Test
  void onlyACustomerAppliesACoupon() {
    stubSuccessfulCheckout();
    var sessionId = startedSessionId();

    applyCouponWith(FakeKeycloak.token("staff-1", "STAFF"), sessionId, "{\"code\": \"X\"}")
        .expectStatus()
        .isForbidden();
    removeCoupon(FakeKeycloak.token("checkout", "CHECKOUT"), sessionId)
        .expectStatus()
        .isForbidden();
    http.delete()
        .uri("/checkout/sessions/{id}/coupon", sessionId)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }
}
