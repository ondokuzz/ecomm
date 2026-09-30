package com.ecomm.promotions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecomm.commons.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * What a Coupon's discount takes off a subtotal: a percentage rounded down to the currency's minor
 * unit, a fixed amount only in its own currency, and never more than the subtotal.
 */
class DiscountCalculationTest {

  @ParameterizedTest
  @CsvSource({
    // percent, subtotal, currency, discount
    "10, 159800, EUR, 15980",
    // 15% of 10.01 EUR is 1.5015 EUR: rounded down to 1.50.
    "15, 1001, EUR, 150",
    // 33% of 0.99 EUR is 0.3267 EUR: rounded down to 0.32.
    "33, 99, EUR, 32",
    // Yen has no minor unit: 10% of 1999 JPY is 199.9, rounded down to 199.
    "10, 1999, JPY, 199",
    // Three-digit minor unit: 7% of 1.005 KWD is 0.07035, rounded down to 0.070.
    "7, 1005, KWD, 70",
    "1, 99, EUR, 0",
    "100, 79900, EUR, 79900"
  })
  void aPercentageIsRoundedDownToTheMinorUnit(
      int percent, long subtotalMinor, String currency, long discountMinor) {
    var discount = new CouponDiscount.PercentOff(percent);

    assertThat(discount.on(Money.of(subtotalMinor, currency)))
        .isEqualTo(Money.of(discountMinor, currency));
  }

  @Test
  void aPercentageOfAHugeSubtotalDoesNotOverflow() {
    var discount = new CouponDiscount.PercentOff(50);

    assertThat(discount.on(Money.of(Long.MAX_VALUE - 1, "EUR")))
        .isEqualTo(Money.of((Long.MAX_VALUE - 1) / 2, "EUR"));
  }

  @Test
  void aFixedAmountIsTakenOffInFull() {
    var discount = new CouponDiscount.AmountOff(Money.of(5000, "EUR"));

    assertThat(discount.on(Money.of(159800, "EUR"))).isEqualTo(Money.of(5000, "EUR"));
  }

  @Test
  void aFixedAmountIsCappedAtTheSubtotal() {
    var discount = new CouponDiscount.AmountOff(Money.of(5000, "EUR"));

    assertThat(discount.on(Money.of(1999, "EUR"))).isEqualTo(Money.of(1999, "EUR"));
  }

  @Test
  void aFixedAmountAppliesOnlyToASubtotalInItsCurrency() {
    var discount = new CouponDiscount.AmountOff(Money.of(5000, "EUR"));

    assertThat(discount.appliesTo(Money.of(159800, "EUR"))).isTrue();
    assertThat(discount.appliesTo(Money.of(159800, "USD"))).isFalse();
    assertThatThrownBy(() -> discount.on(Money.of(159800, "USD")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aPercentageAppliesToAnyCurrency() {
    var discount = new CouponDiscount.PercentOff(10);

    assertThat(discount.appliesTo(Money.of(1999, "JPY"))).isTrue();
    assertThat(discount.on(Money.of(1999, "USD"))).isEqualTo(Money.of(199, "USD"));
  }

  @Test
  void nothingIsTakenOffAZeroSubtotal() {
    assertThat(new CouponDiscount.PercentOff(10).on(Money.of(0, "EUR")))
        .isEqualTo(Money.of(0, "EUR"));
    assertThat(new CouponDiscount.AmountOff(Money.of(5000, "EUR")).on(Money.of(0, "EUR")))
        .isEqualTo(Money.of(0, "EUR"));
  }

  @ParameterizedTest
  @CsvSource({"0", "101", "-5"})
  void aPercentageIsFromOneToAHundred(int percent) {
    assertThatThrownBy(() -> new CouponDiscount.PercentOff(percent))
        .isInstanceOf(InvalidCouponException.class);
  }

  @ParameterizedTest
  @CsvSource({"0", "-100"})
  void aFixedAmountIsPositive(long amountMinor) {
    assertThatThrownBy(() -> new CouponDiscount.AmountOff(Money.of(amountMinor, "EUR")))
        .isInstanceOf(InvalidCouponException.class);
  }
}
