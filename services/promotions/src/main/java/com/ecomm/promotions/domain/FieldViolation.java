package com.ecomm.promotions.domain;

/**
 * One field of what Staff or Checkout sent that is missing or malformed, such as {@code
 * discount.percentOff} or {@code categories[1]}, with what is wrong with it.
 */
public record FieldViolation(String field, String message) {

  @Override
  public String toString() {
    return field + " " + message;
  }
}
