package com.ecomm.catalog.domain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A Product was described with missing or malformed data. {@link #violations()} names each field
 * that breaks its Category's rules; it is empty for other mistakes.
 */
public class InvalidProductException extends RuntimeException {

  private final List<FieldViolation> violations;

  public InvalidProductException(String message) {
    super(message);
    this.violations = List.of();
  }

  public InvalidProductException(List<FieldViolation> violations) {
    super(
        violations.stream()
            .map(FieldViolation::toString)
            .collect(Collectors.joining("; ", "Product breaks its Category's rules: ", "")));
    this.violations = List.copyOf(violations);
  }

  public List<FieldViolation> violations() {
    return violations;
  }
}
