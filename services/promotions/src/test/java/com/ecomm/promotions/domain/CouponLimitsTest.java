package com.ecomm.promotions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * How a Coupon's Usage limits count its redemptions and live Redemption holds: whether one more
 * hold is allowed, and where the Coupon stands.
 */
class CouponLimitsTest {

  private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant UNTIL = Instant.parse("2027-01-01T00:00:00Z");
  private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

  private static final CouponUsage NONE = CouponUsage.NONE;

  @Test
  void aCouponWithoutLimitsIsHeldWhateverItsUse() {
    var unlimited = coupon(null, null);

    assertThatCode(() -> unlimited.checkHold(new CouponUsage(1_000, 50), new CouponUsage(10, 1)))
        .doesNotThrowAnyException();
    assertThat(unlimited.stateAt(NOW, new CouponUsage(1_000, 50))).isEqualTo(CouponState.ACTIVE);
  }

  @Test
  void redemptionsAndHoldsTogetherReachTheUsageLimit() {
    var firstHundred = coupon(100, null);

    assertThatCode(() -> firstHundred.checkHold(new CouponUsage(98, 1), NONE))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> firstHundred.checkHold(new CouponUsage(99, 1), NONE))
        .isInstanceOfSatisfying(
            CouponNotApplicableException.class,
            e -> assertThat(e.rejection()).isEqualTo(CouponRejection.EXHAUSTED));
  }

  @Test
  void theCustomersOwnRedemptionsAndHoldsReachThePerCustomerLimit() {
    var twiceEach = coupon(null, 2);

    assertThatCode(() -> twiceEach.checkHold(new CouponUsage(500, 9), new CouponUsage(1, 0)))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> twiceEach.checkHold(new CouponUsage(500, 9), new CouponUsage(1, 1)))
        .isInstanceOfSatisfying(
            CouponNotApplicableException.class,
            e -> assertThat(e.rejection()).isEqualTo(CouponRejection.ALREADY_REDEEMED));
  }

  @Test
  void aUsedUpCouponIsExhaustedBeforeItIsAlreadyRedeemed() {
    var once = coupon(1, 1);

    assertThatThrownBy(() -> once.checkHold(new CouponUsage(1, 0), new CouponUsage(1, 0)))
        .isInstanceOfSatisfying(
            CouponNotApplicableException.class,
            e -> assertThat(e.rejection()).isEqualTo(CouponRejection.EXHAUSTED));
  }

  @Test
  void aLimitLoweredBelowTheRedemptionsMadeRefusesAndRaisingItAgainAllows() {
    var used = new CouponUsage(5, 0);

    assertThatThrownBy(() -> coupon(3, null).checkHold(used, NONE))
        .isInstanceOf(CouponNotApplicableException.class);
    assertThatCode(() -> coupon(6, null).checkHold(used, NONE)).doesNotThrowAnyException();
  }

  @Test
  void aCouponIsExhaustedOnceItsUseReachesTheLimit() {
    var firstTwo = coupon(2, null);

    assertThat(firstTwo.stateAt(NOW, new CouponUsage(1, 0))).isEqualTo(CouponState.ACTIVE);
    assertThat(firstTwo.stateAt(NOW, new CouponUsage(1, 1))).isEqualTo(CouponState.EXHAUSTED);
    assertThat(firstTwo.stateAt(NOW, new CouponUsage(3, 0))).isEqualTo(CouponState.EXHAUSTED);
  }

  @Test
  void offScheduledAndExpiredComeBeforeExhausted() {
    var usedUp = new CouponUsage(1, 0);

    assertThat(coupon(1, null, false).stateAt(NOW, usedUp)).isEqualTo(CouponState.OFF);
    assertThat(coupon(1, null).stateAt(FROM.minusSeconds(1), usedUp))
        .isEqualTo(CouponState.SCHEDULED);
    assertThat(coupon(1, null).stateAt(UNTIL, usedUp)).isEqualTo(CouponState.EXPIRED);
  }

  @Test
  void itsWindowRunsFromValidFromUpToButNotIncludingValidUntilToTheSecond() {
    var coupon = coupon(null, null);

    assertThat(coupon.stateAt(FROM.minusSeconds(1), NONE)).isEqualTo(CouponState.SCHEDULED);
    assertThat(coupon.stateAt(FROM, NONE)).isEqualTo(CouponState.ACTIVE);
    assertThat(coupon.stateAt(UNTIL.minusSeconds(1), NONE)).isEqualTo(CouponState.ACTIVE);
    assertThat(coupon.stateAt(UNTIL, NONE)).isEqualTo(CouponState.EXPIRED);
  }

  @Test
  void aLimitIsAtLeastOne() {
    assertThatThrownBy(() -> coupon(0, null))
        .isInstanceOfSatisfying(
            InvalidPromotionException.class,
            e -> assertThat(e.violation().field()).isEqualTo("usageLimit"));
    assertThatThrownBy(() -> coupon(null, 0))
        .isInstanceOfSatisfying(
            InvalidPromotionException.class,
            e -> assertThat(e.violation().field()).isEqualTo("perCustomerLimit"));
  }

  private static Coupon coupon(Integer usageLimit, Integer perCustomerLimit) {
    return coupon(usageLimit, perCustomerLimit, true);
  }

  private static Coupon coupon(Integer usageLimit, Integer perCustomerLimit, boolean active) {
    return new Coupon(
        "LIMITED",
        new DiscountRule.PercentOff(10),
        null,
        FROM,
        UNTIL,
        active,
        usageLimit,
        perCustomerLimit);
  }
}
