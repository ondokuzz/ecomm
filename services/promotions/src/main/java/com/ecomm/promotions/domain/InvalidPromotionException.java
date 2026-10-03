package com.ecomm.promotions.domain;

/**
 * A Coupon, a Campaign or an evaluation was sent with missing or malformed data. {@link
 * #violation()} names the field at fault, by its path in the JSON, such as {@code
 * discount.amountOff.currency}.
 */
public class InvalidPromotionException extends RuntimeException {

  private final FieldViolation violation;

  public InvalidPromotionException(String field, String message) {
    super(field + " " + message);
    this.violation = new FieldViolation(field, message);
  }

  public FieldViolation violation() {
    return violation;
  }
}
