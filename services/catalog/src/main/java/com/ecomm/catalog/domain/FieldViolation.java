package com.ecomm.catalog.domain;

/**
 * One field of a Product that breaks its Category's rules, such as {@code attributes.screen} or
 * {@code category}.
 */
public record FieldViolation(String field, String message) {

  @Override
  public String toString() {
    return field + " " + message;
  }
}
