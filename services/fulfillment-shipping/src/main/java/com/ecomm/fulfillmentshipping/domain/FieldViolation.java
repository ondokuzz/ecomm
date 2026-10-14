package com.ecomm.fulfillmentshipping.domain;

/** One field of a request that breaks a rule, such as {@code serialNumbers.PHN-1[0]}. */
public record FieldViolation(String field, String message) {

  @Override
  public String toString() {
    return field + " " + message;
  }
}
