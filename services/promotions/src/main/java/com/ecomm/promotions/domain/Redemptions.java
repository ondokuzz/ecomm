package com.ecomm.promotions.domain;

/** What a Redemption hold and a Coupon redemption share. */
final class Redemptions {

  private Redemptions() {}

  /**
   * @throws InvalidPromotionException when {@code value} is missing or blank
   */
  static String required(String field, String value) {
    if (value == null || value.isBlank()) {
      throw new InvalidPromotionException(field, "is needed");
    }
    return value;
  }
}
