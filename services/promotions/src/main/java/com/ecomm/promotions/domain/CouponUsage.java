package com.ecomm.promotions.domain;

/**
 * How much of a Coupon is taken, by everyone or by one Customer: the redemptions made, and the
 * Redemption holds still live.
 */
public record CouponUsage(int redemptions, int holds) {

  /** Nothing taken. */
  public static final CouponUsage NONE = new CouponUsage(0, 0);

  /** What counts against a Usage limit: redemptions and live holds together. */
  public int taken() {
    return redemptions + holds;
  }
}
