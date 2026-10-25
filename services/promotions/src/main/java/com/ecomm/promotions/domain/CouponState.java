package com.ecomm.promotions.domain;

import java.util.Locale;

/** Where a Coupon stands at a moment, as Staff see it in the list. */
public enum CouponState {
  /** Active, within its validity window, and not used up: it applies. */
  ACTIVE,
  /** Active, but its window hasn't started. */
  SCHEDULED,
  /** Active, but its window has ended. */
  EXPIRED,
  /** Staff have switched it off, whatever its window. */
  OFF,
  /** Within its window, but its redemptions and live holds reach its {@code usageLimit}. */
  EXHAUSTED;

  /**
   * How clients read it: {@code active}, {@code scheduled}, {@code expired}, {@code off} or {@code
   * exhausted}.
   */
  public String label() {
    return name().toLowerCase(Locale.ROOT);
  }
}
