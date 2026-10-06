package com.ecomm.promotions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.commons.money.Money;
import com.ecomm.promotions.domain.Discount.CampaignDiscount;
import com.ecomm.promotions.domain.Discount.CouponDiscount;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * How every Discount a Checkout Session is due stacks: running Campaigns by priority, each on what
 * its lines still come to, then the Coupon on what is left, never more than the subtotal in all.
 */
class DiscountStackingTest {

  private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
  private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant UNTIL = Instant.parse("2027-01-01T00:00:00Z");

  private static final Line HEADPHONES = line("AUD-1", "audio", 1, 20000);
  private static final Line PHONE = line("PHN-1", "phones", 1, 80000);

  @Test
  void withNoCampaignAndNoCouponNothingIsTakenOff() {
    assertThat(DiscountStacking.discounts(List.of(PHONE), List.of(), Optional.empty(), NOW))
        .isEmpty();
  }

  @Test
  void campaignsApplyByPriorityEachOnWhatIsLeft() {
    var second = campaign("Second", 20, percent(10));
    var first = campaign("First", 10, percent(50));

    var discounts =
        DiscountStacking.discounts(List.of(PHONE), List.of(second, first), Optional.empty(), NOW);

    // 50% of 800.00 is 400.00; 10% of the 400.00 left is 40.00.
    assertThat(discounts)
        .containsExactly(
            new CampaignDiscount(first.id(), "First", eur(40000)),
            new CampaignDiscount(second.id(), "Second", eur(4000)));
  }

  @Test
  void aCampaignLimitedToCategoriesDiscountsOnlyTheirLines() {
    var audioWeek = campaign("Audio week", 10, percent(15), List.of("audio"));

    var discounts =
        DiscountStacking.discounts(
            List.of(PHONE, HEADPHONES), List.of(audioWeek), Optional.empty(), NOW);

    assertThat(discounts)
        .containsExactly(new CampaignDiscount(audioWeek.id(), "Audio week", eur(3000)));
  }

  @Test
  void aCampaignWithNoneOfItsCategoriesInTheSessionGivesNothing() {
    var audioWeek = campaign("Audio week", 10, percent(15), List.of("audio"));

    assertThat(
            DiscountStacking.discounts(List.of(PHONE), List.of(audioWeek), Optional.empty(), NOW))
        .isEmpty();
  }

  @Test
  void aCampaignsMinimumIsMeasuredOnItsOwnLines() {
    // The session comes to 1,000.00, but its audio lines only to 200.00.
    var bigAudio =
        new Campaign(
            UUID.randomUUID(),
            "Big audio",
            percent(10),
            List.of("audio"),
            eur(25000),
            FROM,
            UNTIL,
            true,
            10);
    var smallAudio =
        new Campaign(
            UUID.randomUUID(),
            "Small audio",
            percent(10),
            List.of("audio"),
            eur(20000),
            FROM,
            UNTIL,
            true,
            20);

    var discounts =
        DiscountStacking.discounts(
            List.of(PHONE, HEADPHONES), List.of(bigAudio, smallAudio), Optional.empty(), NOW);

    assertThat(discounts)
        .containsExactly(new CampaignDiscount(smallAudio.id(), "Small audio", eur(2000)));
  }

  @Test
  void aMinimumIsMeasuredOnWhatTheLinesComeToBeforeAnyDiscount() {
    var half = campaign("Half", 10, percent(50));
    var over500 =
        new Campaign(
            UUID.randomUUID(),
            "Over 500",
            percent(10),
            List.of(),
            eur(50000),
            FROM,
            UNTIL,
            true,
            20);

    // After 50% off, the phone comes to 400.00, but its 800.00 still reaches the 500.00 minimum;
    // 10% of the 400.00 left is 40.00.
    assertThat(
            DiscountStacking.discounts(
                List.of(PHONE), List.of(half, over500), Optional.empty(), NOW))
        .containsExactly(
            new CampaignDiscount(half.id(), "Half", eur(40000)),
            new CampaignDiscount(over500.id(), "Over 500", eur(4000)));
  }

  @Test
  void aLaterCampaignOnSomeLinesSeesWhatAnEarlierOneLeftOfThem() {
    var everything = campaign("Everything", 10, percent(10));
    var audio = campaign("Audio", 20, percent(50), List.of("audio"));

    var discounts =
        DiscountStacking.discounts(
            List.of(PHONE, HEADPHONES), List.of(everything, audio), Optional.empty(), NOW);

    // 10% of 1,000.00 is 100.00, of which the headphones bear 20.00; half of their 180.00 is 90.00.
    assertThat(discounts)
        .containsExactly(
            new CampaignDiscount(everything.id(), "Everything", eur(10000)),
            new CampaignDiscount(audio.id(), "Audio", eur(9000)));
  }

  @Test
  void theCouponAppliesLastOnWhatIsLeft() {
    var audioWeek = campaign("Audio week", 10, percent(15), List.of("audio"));
    var welcome = coupon("WELCOME10", percent(10), null);

    var discounts =
        DiscountStacking.discounts(
            List.of(PHONE, HEADPHONES), List.of(audioWeek), Optional.of(welcome), NOW);

    // 15% of the 200.00 headphones is 30.00; 10% of the 970.00 left is 97.00.
    assertThat(discounts)
        .containsExactly(
            new CampaignDiscount(audioWeek.id(), "Audio week", eur(3000)),
            new CouponDiscount("WELCOME10", eur(9700)));
  }

  @Test
  void aCouponsMinimumIsMeasuredOnTheSubtotalBeforeAnyDiscount() {
    var half = campaign("Half", 10, percent(50));
    var over500 = coupon("OVER500", percent(10), eur(50000));
    var over900 = coupon("OVER900", percent(10), eur(90000));

    // The Campaign leaves 400.00 of the 800.00 phone: the Coupon reaches its minimum on the
    // 800.00, and takes 10% of the 400.00.
    assertThat(DiscountStacking.discounts(List.of(PHONE), List.of(half), Optional.of(over500), NOW))
        .last()
        .isEqualTo(new CouponDiscount("OVER500", eur(4000)));
    assertThatThrownBy(
            () ->
                DiscountStacking.discounts(
                    List.of(PHONE), List.of(half), Optional.of(over900), NOW))
        .isInstanceOfSatisfying(
            CouponNotApplicableException.class,
            e -> assertThat(e.rejection()).isEqualTo(CouponRejection.BELOW_MINIMUM));
  }

  @Test
  void percentagesRoundDownToTheMinorUnit() {
    // 15% of 10.01 is 1.5015, so 1.50; 33% of the 8.51 left is 2.8083, so 2.80.
    var odd = line("ODD-1", "audio", 1, 1001);
    var first = campaign("First", 10, percent(15));
    var coupon = coupon("THIRD", percent(33), null);

    assertThat(DiscountStacking.discounts(List.of(odd), List.of(first), Optional.of(coupon), NOW))
        .extracting(Discount::amount)
        .containsExactly(eur(150), eur(280));
  }

  @Test
  void theDiscountsNeverComeToMoreThanTheSubtotal() {
    var fifty =
        new Campaign(
            UUID.randomUUID(),
            "Fifty off",
            amountOff(eur(50000)),
            List.of(),
            null,
            FROM,
            UNTIL,
            true,
            10);
    var anotherFifty =
        new Campaign(
            UUID.randomUUID(),
            "Another fifty off",
            amountOff(eur(50000)),
            List.of(),
            null,
            FROM,
            UNTIL,
            true,
            20);
    var everything = coupon("ALL", percent(100), null);

    var discounts =
        DiscountStacking.discounts(
            List.of(PHONE), List.of(fifty, anotherFifty), Optional.of(everything), NOW);

    // 500.00 off, then the 300.00 left, then nothing is left for the Coupon.
    assertThat(discounts)
        .extracting(Discount::amount)
        .containsExactly(eur(50000), eur(30000), eur(0));
  }

  @Test
  void aCampaignThatWouldTakeNothingOffIsLeftOut() {
    var tiny = line("TINY-1", "audio", 1, 99);
    var onePercent = campaign("One percent", 10, percent(1));

    assertThat(
            DiscountStacking.discounts(List.of(tiny), List.of(onePercent), Optional.empty(), NOW))
        .isEmpty();
  }

  @Test
  void aCampaignOnlyAppliesWhileItRuns() {
    var off =
        new Campaign(
            UUID.randomUUID(), "Off", percent(10), List.of(), null, FROM, UNTIL, false, 10);
    var later =
        new Campaign(
            UUID.randomUUID(),
            "Later",
            percent(10),
            List.of(),
            null,
            UNTIL,
            UNTIL.plusSeconds(60),
            true,
            20);
    var over =
        new Campaign(
            UUID.randomUUID(),
            "Over",
            percent(10),
            List.of(),
            null,
            FROM.minusSeconds(60),
            FROM,
            true,
            30);

    assertThat(
            DiscountStacking.discounts(
                List.of(PHONE), List.of(off, later, over), Optional.empty(), NOW))
        .isEmpty();
  }

  @Test
  void aCampaignWithAnAmountOrMinimumInAnotherCurrencyDoesNotApply() {
    var dollarsOff =
        new Campaign(
            UUID.randomUUID(),
            "Dollars off",
            amountOff(Money.of(1000, "USD")),
            List.of(),
            null,
            FROM,
            UNTIL,
            true,
            10);
    var dollarMinimum =
        new Campaign(
            UUID.randomUUID(),
            "Dollar minimum",
            percent(10),
            List.of(),
            Money.of(100, "USD"),
            FROM,
            UNTIL,
            true,
            20);
    var euros = campaign("Euros", 30, percent(10));

    assertThat(
            DiscountStacking.discounts(
                List.of(PHONE), List.of(dollarsOff, dollarMinimum, euros), Optional.empty(), NOW))
        .containsExactly(new CampaignDiscount(euros.id(), "Euros", eur(8000)));
  }

  @Test
  void aCouponInAnotherCurrencyIsACurrencyMismatch() {
    var dollars = coupon("DOLLARS", amountOff(Money.of(1000, "USD")), null);

    assertThatThrownBy(
            () -> DiscountStacking.discounts(List.of(PHONE), List.of(), Optional.of(dollars), NOW))
        .isInstanceOfSatisfying(
            CouponNotApplicableException.class,
            e -> assertThat(e.rejection()).isEqualTo(CouponRejection.CURRENCY_MISMATCH));
  }

  @Test
  void aCampaignsShareOfEachLineAddsUpToItsAmount() {
    // 10% of 3 × 3.33 and 1 × 0.01, 10.00 in all, is 1.00, spread so a later audio Campaign sees
    // exactly what is left of the audio line.
    var odd = line("ODD-1", "phones", 3, 333);
    var penny = line("PENNY-1", "audio", 1, 1);
    var tenth = campaign("Tenth", 10, percent(10));
    var allAudio = campaign("All audio", 20, percent(100), List.of("audio"));

    var discounts =
        DiscountStacking.discounts(
            List.of(odd, penny), List.of(tenth, allAudio), Optional.empty(), NOW);

    // The 1.00 falls 0.999 on the phones and 0.001 on the penny: rounded, all of it on the phones.
    assertThat(discounts).extracting(Discount::amount).containsExactly(eur(100), eur(1));
  }

  @Test
  void theLinesMustBeInOneCurrency() {
    var dollars = new Line("USD-1", "USD-1", "phones", 1, Money.of(100, "USD"));

    assertThatThrownBy(
            () ->
                DiscountStacking.discounts(
                    List.of(PHONE, dollars), List.of(), Optional.empty(), NOW))
        .isInstanceOf(InvalidPromotionException.class)
        .hasMessageContaining("lines");
    assertThatThrownBy(
            () -> DiscountStacking.discounts(List.of(), List.of(), Optional.empty(), NOW))
        .isInstanceOf(InvalidPromotionException.class);
  }

  private static Line line(String variantId, String category, int quantity, long unitMinor) {
    return new Line(variantId, variantId, category, quantity, eur(unitMinor));
  }

  private static Campaign campaign(String name, int priority, DiscountRule rule) {
    return campaign(name, priority, rule, List.of());
  }

  private static Campaign campaign(
      String name, int priority, DiscountRule rule, List<String> categories) {
    return new Campaign(
        UUID.randomUUID(), name, rule, categories, null, FROM, UNTIL, true, priority);
  }

  private static Coupon coupon(String code, DiscountRule rule, Money minimum) {
    return new Coupon(code, rule, minimum, FROM, UNTIL, true);
  }

  private static DiscountRule percent(int percent) {
    return new DiscountRule.PercentOff(percent);
  }

  private static DiscountRule amountOff(Money amount) {
    return new DiscountRule.AmountOff(amount);
  }

  private static Money eur(long minor) {
    return Money.of(minor, "EUR");
  }
}
