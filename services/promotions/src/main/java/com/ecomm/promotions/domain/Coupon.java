package com.ecomm.promotions.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A code a Customer enters at checkout for a discount. It applies while it is {@code active}, from
 * {@code validFrom} up to but not including {@code validUntil}, to a subtotal of at least its
 * optional {@code minimumSubtotal}. Its code is stored and matched upper-case. Throws {@link
 * InvalidPromotionException}, naming the field, unless every field is valid.
 */
public record Coupon(
    String code,
    DiscountRule discount,
    Money minimumSubtotal,
    Instant validFrom,
    Instant validUntil,
    boolean active) {

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
  }

  /** {@code code} as Coupons are stored and matched: upper-case. */
  public static String normalize(String code) {
    return code.toUpperCase(Locale.ROOT);
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
