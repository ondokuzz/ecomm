package com.ecomm.promotions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** A fresh stack has one demo Coupon, so the demo checkout works out of the box. */
class SeedApiTest extends PromotionsApiTest {

  @Test
  void welcome10IsTenPercentOffWithNoMinimum() {
    read("WELCOME10")
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"code": "WELCOME10", "discount": {"type": "PERCENT_OFF", "percentOff": 10,
             "amountOff": null},
             "minimumSubtotal": null, "active": true}
            """);
  }

  @Test
  void welcome10IsValidNowAndForAYear() {
    var coupon = read("WELCOME10").expectBody(Validity.class).returnResult().getResponseBody();

    var now = Instant.now();
    assertThat(coupon.validFrom()).isBeforeOrEqualTo(now);
    assertThat(coupon.validUntil()).isAfter(now.plus(Duration.ofDays(364)));
    assertThat(Duration.between(coupon.validFrom(), coupon.validUntil()))
        .isBetween(Duration.ofDays(365), Duration.ofDays(366));
  }

  @Test
  void welcome10TakesTenPercentOff() {
    evaluate("welcome10", 159800, "EUR")
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {"couponCode": "WELCOME10", "discount": {"amountMinor": 15980, "currency": "EUR"}}
            """);
  }

  record Validity(Instant validFrom, Instant validUntil) {}
}
