package com.ecomm.catalog.domain;

/**
 * A Category was described with missing or malformed data. {@link #violation()} names the field,
 * such as {@code name} or {@code attributes[1].values}; an attribute definition names its own
 * fields ({@code name}, {@code type}, {@code values}) until {@link #within(String)} places it.
 */
public class InvalidCategoryException extends RuntimeException {

  private final FieldViolation violation;

  public InvalidCategoryException(String field, String message) {
    this(new FieldViolation(field, message));
  }

  private InvalidCategoryException(FieldViolation violation) {
    super("Category is invalid: " + violation);
    this.violation = violation;
  }

  public FieldViolation violation() {
    return violation;
  }

  /**
   * The same mistake, with its field placed inside {@code parent}, such as {@code attributes[2]}.
   */
  public InvalidCategoryException within(String parent) {
    return new InvalidCategoryException(
        new FieldViolation(parent + "." + violation.field(), violation.message()));
  }
}
