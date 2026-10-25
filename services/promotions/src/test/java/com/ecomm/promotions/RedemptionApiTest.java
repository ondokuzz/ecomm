package com.ecomm.promotions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Checkout holds a Coupon's redemption for a Checkout Session, refused once its Usage limits are
 * reached, and releases it; Orchestration's checkout Saga redeems it once the session is paid.
 */
class RedemptionApiTest extends PromotionsApiTest {

  private static final Duration SESSION = Duration.ofMinutes(15);

  @Test
  void staffGiveACouponLimitsAndReadItsUse() {
    created(limited("LIM-READ", 100, 1));

    read("LIM-READ")
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"code": "LIM-READ", "usageLimit": 100, "perCustomerLimit": 1,
             "redemptions": 0, "holds": 0, "state": "active"}
            """);
  }

  @Test
  void aCouponWithoutLimitsIsUnlimited() {
    created(percentOff("LIM-NONE", 10));

    read("LIM-NONE")
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"usageLimit": null, "perCustomerLimit": null, "redemptions": 0, "holds": 0,
             "state": "active"}
            """);
    for (var i = 0; i < 3; i++) {
      hold(session(), "customer-" + i, "LIM-NONE", inASession()).expectStatus().isNoContent();
    }
    read("LIM-NONE").expectBody().jsonPath("$.holds").isEqualTo(3);
  }

  @Test
  void holdsUpToTheUsageLimitThenTheCouponIsExhausted() {
    created(limited("LIM-TWO", 2, null));

    hold(session(), "customer-1", "lim-two", inASession()).expectStatus().isNoContent();
    hold(session(), "customer-2", "LIM-TWO", inASession()).expectStatus().isNoContent();

    expectRefused(hold(session(), "customer-3", "LIM-TWO", inASession()), "exhausted");
    read("LIM-TWO")
        .expectBody()
        .json("{\"redemptions\": 0, \"holds\": 2, \"state\": \"exhausted\"}");
  }

  @Test
  void aCustomerReachingThePerCustomerLimitHasAlreadyRedeemedIt() {
    created(limited("LIM-ONCE", null, 1));
    var first = session();
    hold(first, "customer-1", "LIM-ONCE", inASession()).expectStatus().isNoContent();
    redeem(first, "customer-1", "LIM-ONCE").expectStatus().isNoContent();

    expectRefused(hold(session(), "customer-1", "LIM-ONCE", inASession()), "alreadyRedeemed");
    hold(session(), "customer-2", "LIM-ONCE", inASession()).expectStatus().isNoContent();
  }

  @Test
  void holdingAgainForTheSameSessionDoesntCountItTwice() {
    created(limited("LIM-AGAIN", 1, null));
    var session = session();

    hold(session, "customer-1", "LIM-AGAIN", inASession()).expectStatus().isNoContent();
    hold(session, "customer-1", "LIM-AGAIN", inASession()).expectStatus().isNoContent();

    read("LIM-AGAIN").expectBody().jsonPath("$.holds").isEqualTo(1);
  }

  @Test
  void holdingAnotherCouponForTheSessionReleasesTheFirst() {
    created(limited("LIM-FIRST", 1, null));
    created(percentOff("LIM-SECOND", 5));
    var session = session();
    hold(session, "customer-1", "LIM-FIRST", inASession()).expectStatus().isNoContent();

    hold(session, "customer-1", "LIM-SECOND", inASession()).expectStatus().isNoContent();

    read("LIM-FIRST").expectBody().json("{\"holds\": 0, \"state\": \"active\"}");
    hold(session(), "customer-2", "LIM-FIRST", inASession()).expectStatus().isNoContent();
  }

  @Test
  void aRefusedHoldLeavesTheSessionsHoldAsItWas() {
    created(percentOff("LIM-KEPT", 5));
    created(limited("LIM-FULL", 1, null));
    hold(session(), "customer-9", "LIM-FULL", inASession()).expectStatus().isNoContent();
    var session = session();
    hold(session, "customer-1", "LIM-KEPT", inASession()).expectStatus().isNoContent();

    expectRefused(hold(session, "customer-1", "LIM-FULL", inASession()), "exhausted");

    read("LIM-KEPT").expectBody().jsonPath("$.holds").isEqualTo(1);
  }

  @Test
  void aHoldLapsesAtTheSessionsExpiry() {
    var now = Instant.parse("2030-03-01T10:00:00Z");
    time.set(now);
    created(limited("LIM-LAPSE", 1, null));
    hold(session(), "customer-1", "LIM-LAPSE", now.plus(SESSION)).expectStatus().isNoContent();

    time.set(now.plus(SESSION).minusSeconds(1));
    expectRefused(hold(session(), "customer-2", "LIM-LAPSE", now.plus(SESSION)), "exhausted");

    time.set(now.plus(SESSION));
    read("LIM-LAPSE").expectBody().json("{\"holds\": 0, \"state\": \"active\"}");
    hold(session(), "customer-2", "LIM-LAPSE", now.plus(SESSION).plus(SESSION))
        .expectStatus()
        .isNoContent();
  }

  @Test
  void releasingAHoldFreesItsUse() {
    created(limited("LIM-RELEASE", 1, null));
    var session = session();
    hold(session, "customer-1", "LIM-RELEASE", inASession()).expectStatus().isNoContent();

    release(session).expectStatus().isNoContent();
    release(session).expectStatus().isNoContent();

    read("LIM-RELEASE").expectBody().jsonPath("$.holds").isEqualTo(0);
    hold(session(), "customer-2", "LIM-RELEASE", inASession()).expectStatus().isNoContent();
  }

  @Test
  void releasingASessionThatHoldsNothingChangesNothing() {
    release(session()).expectStatus().isNoContent();
  }

  @Test
  void redeemingTurnsTheSessionsHoldIntoARedemptionOnce() {
    created(limited("LIM-REDEEM", 5, null));
    var session = session();
    hold(session, "customer-1", "LIM-REDEEM", inASession()).expectStatus().isNoContent();

    redeem(session, "customer-1", "lim-redeem").expectStatus().isNoContent();
    redeem(session, "customer-1", "LIM-REDEEM").expectStatus().isNoContent();

    read("LIM-REDEEM").expectBody().json("{\"redemptions\": 1, \"holds\": 0}");
  }

  @Test
  void aSessionWithoutAHoldIsRedeemedAnywayEvenPastTheLimit() {
    created(limited("LIM-OVER", 1, null));
    var held = session();
    hold(held, "customer-1", "LIM-OVER", inASession()).expectStatus().isNoContent();

    // A session from before limits, or one whose hold lapsed while its Saga ran.
    redeem(session(), "customer-2", "LIM-OVER").expectStatus().isNoContent();
    redeem(held, "customer-1", "LIM-OVER").expectStatus().isNoContent();

    read("LIM-OVER")
        .expectBody()
        .json("{\"redemptions\": 2, \"holds\": 0, \"state\": \"exhausted\"}");
  }

  @Test
  void aCouponThatEndedOrWasSwitchedOffWhileHeldIsStillRedeemed() {
    var now = Instant.parse("2031-05-01T10:00:00Z");
    time.set(now);
    created(
        """
        {"code": "LIM-ENDING", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2031-01-01T00:00:00Z", "validUntil": "2031-05-01T10:05:00Z",
         "active": true, "usageLimit": 10}
        """);
    var ending = session();
    var switchedOff = session();
    hold(ending, "customer-1", "LIM-ENDING", now.plus(SESSION)).expectStatus().isNoContent();
    hold(switchedOff, "customer-2", "LIM-ENDING", now.plus(SESSION)).expectStatus().isNoContent();

    time.set(now.plus(Duration.ofMinutes(10)));
    redeem(ending, "customer-1", "LIM-ENDING").expectStatus().isNoContent();
    update(
            "LIM-ENDING",
            """
            {"discount": {"type": "PERCENT_OFF", "percentOff": 10},
             "validFrom": "2031-01-01T00:00:00Z", "validUntil": "2031-05-01T10:05:00Z",
             "active": false, "usageLimit": 10}
            """)
        .expectStatus()
        .isOk();
    redeem(switchedOff, "customer-2", "LIM-ENDING").expectStatus().isNoContent();

    read("LIM-ENDING").expectBody().json("{\"redemptions\": 2, \"state\": \"off\"}");
  }

  @Test
  void loweringALimitKeepsHoldsAndRefusesNewOnesAndRaisingItReopens() {
    created(limited("LIM-ADJUST", 3, null));
    for (var i = 0; i < 3; i++) {
      var session = session();
      hold(session, "customer-" + i, "LIM-ADJUST", inASession()).expectStatus().isNoContent();
      if (i < 2) {
        redeem(session, "customer-" + i, "LIM-ADJUST").expectStatus().isNoContent();
      }
    }

    update("LIM-ADJUST", limited("LIM-ADJUST", 1, null)).expectStatus().isOk();
    read("LIM-ADJUST")
        .expectBody()
        .json("{\"redemptions\": 2, \"holds\": 1, \"state\": \"exhausted\"}");
    expectRefused(hold(session(), "customer-7", "LIM-ADJUST", inASession()), "exhausted");

    update("LIM-ADJUST", limited("LIM-ADJUST", 4, null)).expectStatus().isOk();
    read("LIM-ADJUST").expectBody().jsonPath("$.state").isEqualTo("active");
    hold(session(), "customer-7", "LIM-ADJUST", inASession()).expectStatus().isNoContent();
  }

  @Test
  void theListGivesEachCouponsUseAndState() {
    created(limited("LIM-LIST", 1, null));
    hold(session(), "customer-1", "LIM-LIST", inASession()).expectStatus().isNoContent();

    http.get()
        .uri("/coupons")
        .headers(h -> h.setBearerAuth(staffToken()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[?(@.code == 'LIM-LIST')].holds")
        .isEqualTo(1)
        .jsonPath("$[?(@.code == 'LIM-LIST')].state")
        .isEqualTo("exhausted");
  }

  @Test
  void anUnknownCouponCantBeHeld() {
    expectRefused(hold(session(), "customer-1", "LIM-NOBODY", inASession()), "unknown");
  }

  @Test
  void deletingACouponDropsItsHoldsButNeverGivesBackARedemption() {
    created(limited("LIM-GONE", 5, 1));
    var redeemed = session();
    hold(redeemed, "customer-1", "LIM-GONE", inASession()).expectStatus().isNoContent();
    redeem(redeemed, "customer-1", "LIM-GONE").expectStatus().isNoContent();
    hold(session(), "customer-2", "LIM-GONE", inASession()).expectStatus().isNoContent();
    var paidAfterDeletion = session();
    hold(paidAfterDeletion, "customer-3", "LIM-GONE", inASession()).expectStatus().isNoContent();

    delete("LIM-GONE").expectStatus().isNoContent();
    // A Saga still running for a session priced with it redeems it all the same.
    redeem(paidAfterDeletion, "customer-3", "LIM-GONE").expectStatus().isNoContent();
    created(limited("LIM-GONE", 5, 1));

    read("LIM-GONE").expectBody().json("{\"redemptions\": 2, \"holds\": 0}");
    expectRefused(hold(session(), "customer-1", "LIM-GONE", inASession()), "alreadyRedeemed");
  }

  @Test
  void aLimitBelowOneIsABadRequest() {
    create(limited("LIM-ZERO", 0, null))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("usageLimit");
    create(
            """
            {"code": "LIM-TEXT", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
             "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z",
             "active": true, "perCustomerLimit": "1"}
            """)
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("perCustomerLimit");
  }

  @Test
  void aHoldOrRedemptionMissingAFieldIsABadRequest() {
    hold(
            checkoutToken(),
            "{\"sessionId\": \"s-1\", \"customerId\": \"c-1\", \"couponCode\": \"X\"}")
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("expiresAt");
    redeem(orchestrationToken(), "{\"sessionId\": \"s-1\", \"couponCode\": \"X\"}")
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("customerId");
  }

  @Test
  void onlyCheckoutHoldsAndOnlyOrchestrationRedeems() {
    var holdBody =
        """
        {"sessionId": "s-1", "customerId": "c-1", "couponCode": "X",
         "expiresAt": "2100-01-01T00:00:00Z"}
        """;
    var redeemBody = "{\"sessionId\": \"s-1\", \"customerId\": \"c-1\", \"couponCode\": \"X\"}";
    for (var token : new String[] {staffToken(), customerToken(), orchestrationToken()}) {
      hold(token, holdBody).expectStatus().isForbidden();
      http.delete()
          .uri("/redemptions/holds/s-1")
          .headers(h -> h.setBearerAuth(token))
          .exchange()
          .expectStatus()
          .isForbidden();
    }
    for (var token : new String[] {staffToken(), customerToken(), checkoutToken()}) {
      redeem(token, redeemBody).expectStatus().isForbidden();
    }
    http.post()
        .uri("/redemptions")
        .contentType(MediaType.APPLICATION_JSON)
        .body(redeemBody)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  private static String session() {
    return UUID.randomUUID().toString();
  }

  private Instant inASession() {
    return time.now().plus(SESSION);
  }

  /** An active Coupon for 10% off, valid from 2020 until 2100, with these Usage limits. */
  private static String limited(String code, Integer usageLimit, Integer perCustomerLimit) {
    return """
        {"code": "%s", "discount": {"type": "PERCENT_OFF", "percentOff": 10},
         "validFrom": "2020-01-01T00:00:00Z", "validUntil": "2100-01-01T00:00:00Z", "active": true,
         "usageLimit": %s, "perCustomerLimit": %s}
        """
        .formatted(code, usageLimit, perCustomerLimit);
  }

  private static void expectRefused(RestTestClient.ResponseSpec response, String reason) {
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
