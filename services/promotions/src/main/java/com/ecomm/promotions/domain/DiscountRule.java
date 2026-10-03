package com.ecomm.promotions.domain;

import com.ecomm.commons.money.Money;
import java.math.BigInteger;

/**
 * What a Coupon or a Campaign takes off a subtotal: a percentage of it, or a fixed amount. Either
 * way it is never more than the subtotal.
 */
public sealed interface DiscountRule {

  /** Whether this discount can apply to a subtotal in {@code subtotal}'s currency. */
  boolean appliesTo(Money subtotal);

  /**
   * The amount taken off {@code subtotal}, in its currency.
   *
   * @throws IllegalArgumentException unless it {@link #appliesTo} the subtotal
   */
  Money on(Money subtotal);

  /** {@code percent}% off, from 1 to 100, rounded down to the currency's minor unit. */
  record PercentOff(int percent) implements DiscountRule {

    public PercentOff {
      if (percent < 1 || percent > 100) {
        throw new InvalidPromotionException(
            "discount.percentOff", "must be an integer from 1 to 100");
      }
    }

    @Override
    public boolean appliesTo(Money subtotal) {
      return true;
    }

    @Override
    public Money on(Money subtotal) {
      // Money is already in the minor unit, so rounding down is integer division; BigInteger keeps
      // a huge subtotal from overflowing on the way.
      var discount =
          BigInteger.valueOf(subtotal.amountMinor())
              .multiply(BigInteger.valueOf(percent))
              .divide(BigInteger.valueOf(100))
              .longValueExact();
      return new Money(discount, subtotal.currency());
    }
  }

  /** A fixed amount off, which applies only to a subtotal in the same currency. */
  record AmountOff(Money amount) implements DiscountRule {

    public AmountOff {
      if (amount == null) {
        throw new InvalidPromotionException(
            "discount.amountOff", "is needed for an AMOUNT_OFF discount");
      }
      if (amount.amountMinor() <= 0) {
        throw new InvalidPromotionException("discount.amountOff", "must be positive");
      }
    }

    @Override
    public boolean appliesTo(Money subtotal) {
      return amount.currency().equals(subtotal.currency());
    }

    @Override
    public Money on(Money subtotal) {
      if (!appliesTo(subtotal)) {
        throw new IllegalArgumentException(
            "an amount off in " + amount.currency() + " can't apply to " + subtotal.currency());
      }
      return new Money(Math.min(amount.amountMinor(), subtotal.amountMinor()), subtotal.currency());
    }
  }
}
