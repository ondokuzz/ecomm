package com.ecomm.promotions.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A code a Customer enters at checkout for a discount. It applies while it is {@code active}, from
 * {@code validFrom} up to but not including {@code validUntil}, to a subtotal of at least its
 * optional {@code minimumSubtotal}. Its code is stored and matched upper-case. Throws {@link
 * InvalidCouponException} unless every field is valid.
 */
public record Coupon(
    String code,
    CouponDiscount discount,
    Money minimumSubtotal,
    Instant validFrom,
    Instant validUntil,
    boolean active) {

  /** The longest code a Coupon can have, which is also the longest an Order can record. */
  public static final int MAX_CODE_LENGTH = 64;

  private static final Pattern CODE = Pattern.compile("[A-Z0-9_-]{1," + MAX_CODE_LENGTH + "}");

  public Coupon {
    if (code == null || !CODE.matcher(normalize(code)).matches()) {
      throw new InvalidCouponException(
          "code must be 1 to "
              + MAX_CODE_LENGTH
              + " letters, digits, hyphens or underscores, without spaces");
    }
    code = normalize(code);
    if (discount == null) {
      throw new InvalidCouponException("a Coupon needs a discount");
    }
    if (minimumSubtotal != null && minimumSubtotal.amountMinor() < 0) {
      throw new InvalidCouponException("minimumSubtotal can't be negative");
    }
    if (validFrom == null || validUntil == null) {
      throw new InvalidCouponException("a Coupon needs validFrom and validUntil");
    }
    if (!validUntil.isAfter(validFrom)) {
      throw new InvalidCouponException("validUntil must be after validFrom");
    }
  }

  /** {@code code} as Coupons are stored and matched: upper-case. */
  public static String normalize(String code) {
    return code.toUpperCase(Locale.ROOT);
  }

  /**
   * What this Coupon takes off {@code subtotal} at {@code now}.
   *
   * @throws CouponNotApplicableException when it doesn't apply, saying why
   */
  public Money discountOn(Money subtotal, Instant now) {
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
    return discount.on(subtotal);
  }
}
