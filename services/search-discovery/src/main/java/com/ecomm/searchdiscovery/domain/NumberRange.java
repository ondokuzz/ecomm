package com.ecomm.searchdiscovery.domain;

import java.math.BigDecimal;
import java.util.Optional;

/** A range of a NUMBER attribute's values, inclusive at both ends; a null end is open. */
public record NumberRange(BigDecimal min, BigDecimal max) {

  public NumberRange {
    if (min != null && max != null && min.compareTo(max) > 0) {
      throw new InvalidSearchException("a range's min is above its max");
    }
  }

  /**
   * Whether {@code value}, read as a decimal, lies in the range; a value that isn't one doesn't.
   */
  public boolean contains(String value) {
    return decimal(value)
        .filter(
            n -> (min == null || n.compareTo(min) >= 0) && (max == null || n.compareTo(max) <= 0))
        .isPresent();
  }

  /** {@code value} as a decimal, if Catalog's NUMBER rules can read it as one. */
  static Optional<BigDecimal> decimal(String value) {
    try {
      return Optional.of(new BigDecimal(value.trim()));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
