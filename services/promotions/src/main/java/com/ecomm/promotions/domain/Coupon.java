package com.ecomm.promotions.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A code a Customer enters at checkout for a discount. It applies while it is {@code active}, from
 * {@code validFrom} up to but not including {@code validUntil}, to a subtotal of at least its
 * optional {@code minimumSubtotal}. Its code is stored and matched upper-case.
 *
 * <p>Its optional Usage limits cap how often it is used: {@code usageLimit} in all, {@code
 * perCustomerLimit} by each Customer. Redemptions and live Redemption holds count against both; a
 * Coupon without them is used without limit. Throws {@link InvalidPromotionException}, naming the
 * field, unless every field is valid.
 */
public record Coupon(
    String code,
    DiscountRule discount,
    Money minimumSubtotal,
    Instant validFrom,
    Instant validUntil,
    boolean active,
    Integer usageLimit,
    Integer perCustomerLimit) {

  /** The longest code a Coupon can have, which is also the longest an Order can record. */
  public static final int MAX_CODE_LENGTH = 64;

  private static final Pattern CODE = Pattern.compile("[A-Z0-9_-]{1," + MAX_CODE_LENGTH + "}");

  public Coupon {
    if (code == null || !CODE.matcher(normalize(code)).matches()) {
      throw new InvalidPromotionException(
          "code",
          "must be 1 to "
              + MAX_CODE_LENGTH
              + " letters, digits, hyphens or underscores, without spaces");
    }
    code = normalize(code);
    Terms.check(discount, minimumSubtotal, validFrom, validUntil);
    if (usageLimit != null && usageLimit < 1) {
      throw new InvalidPromotionException("usageLimit", "must be at least 1");
    }
    if (perCustomerLimit != null && perCustomerLimit < 1) {
      throw new InvalidPromotionException("perCustomerLimit", "must be at least 1");
    }
  }

  /** {@code code} as Coupons are stored and matched: upper-case. */
  public static String normalize(String code) {
    return code.toUpperCase(Locale.ROOT);
  }

  /** Where this Coupon stands at {@code now}, with {@code usage} its use by everyone. */
  public CouponState stateAt(Instant now, CouponUsage usage) {
    if (!active) {
      return CouponState.OFF;
    }
    if (now.isBefore(validFrom)) {
      return CouponState.SCHEDULED;
    }
    if (!now.isBefore(validUntil)) {
      return CouponState.EXPIRED;
    }
    return usedUp(usage) ? CouponState.EXHAUSTED : CouponState.ACTIVE;
  }

  /**
   * Checks that one more Redemption hold is allowed, given what everyone and the Customer have
   * taken, without counting the hold it would replace. Holds already made are never taken back, so
   * a limit lowered below them only refuses new ones.
   *
   * @throws CouponNotApplicableException {@code exhausted} when {@code everyone}'s use reaches
   *     {@code usageLimit}, or else {@code alreadyRedeemed} when {@code theCustomer}'s reaches
   *     {@code perCustomerLimit}
   */
  public void checkHold(CouponUsage everyone, CouponUsage theCustomer) {
    if (usedUp(everyone)) {
      throw new CouponNotApplicableException(code, CouponRejection.EXHAUSTED);
    }
    if (perCustomerLimit != null && theCustomer.taken() >= perCustomerLimit) {
      throw new CouponNotApplicableException(code, CouponRejection.ALREADY_REDEEMED);
    }
  }

  private boolean usedUp(CouponUsage everyone) {
    return usageLimit != null && everyone.taken() >= usageLimit;
  }

  /**
   * What this Coupon takes off at {@code now}: its rule applied to {@code remaining}, what the
   * lines still come to after any Campaigns' Discounts, while its minimum is measured on {@code
   * subtotal}, what they came to before any. Both are in the same currency.
   *
   * @throws CouponNotApplicableException when it doesn't apply, saying why
   */
  public Money discountOn(Money remaining, Money subtotal, Instant now) {
    if (!active) {
      throw new CouponNotApplicableException(code, CouponRejection.INACTIVE);
    }
    if (now.isBefore(validFrom)) {
      throw new CouponNotApplicableException(code, CouponRejection.NOT_YET_VALID);
    }
    if (!now.isBefore(validUntil)) {
      throw new CouponNotApplicableException(code, CouponRejection.EXPIRED);
    }
    if (!discount.appliesTo(subtotal)
        || (minimumSubtotal != null && !minimumSubtotal.currency().equals(subtotal.currency()))) {
      throw new CouponNotApplicableException(code, CouponRejection.CURRENCY_MISMATCH);
    }
    if (minimumSubtotal != null && subtotal.amountMinor() < minimumSubtotal.amountMinor()) {
      throw new CouponNotApplicableException(code, CouponRejection.BELOW_MINIMUM);
    }
    return discount.on(remaining);
  }
}
