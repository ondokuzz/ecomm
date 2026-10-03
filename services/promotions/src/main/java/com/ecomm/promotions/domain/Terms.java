package com.ecomm.promotions.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;

/** The terms a Coupon and a Campaign share: a discount, an optional minimum, a validity window. */
final class Terms {

  private Terms() {}

  /**
   * @throws InvalidPromotionException naming the first field that is missing or out of range
   */
  static void check(
      DiscountRule discount, Money minimumSubtotal, Instant validFrom, Instant validUntil) {
    if (discount == null) {
      throw new InvalidPromotionException("discount", "is needed");
    }
    if (minimumSubtotal != null && minimumSubtotal.amountMinor() < 0) {
      throw new InvalidPromotionException("minimumSubtotal", "can't be negative");
    }
    if (validFrom == null) {
      throw new InvalidPromotionException("validFrom", "is needed");
    }
    if (validUntil == null) {
      throw new InvalidPromotionException("validUntil", "is needed");
    }
    if (!validUntil.isAfter(validFrom)) {
      throw new InvalidPromotionException("validUntil", "must be after validFrom");
    }
  }
}
