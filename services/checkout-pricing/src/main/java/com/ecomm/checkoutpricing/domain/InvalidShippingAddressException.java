package com.ecomm.checkoutpricing.domain;

import java.util.List;

/** A Shipping address with something wrong in one or more of its fields, each named. */
public class InvalidShippingAddressException extends RuntimeException {

  private final List<FieldViolation> violations;

  public InvalidShippingAddressException(List<FieldViolation> violations) {
    super(
        "The Shipping address isn't valid: "
            + String.join(
                ", ", violations.stream().map(v -> v.field() + " " + v.message()).toList()));
    this.violations = List.copyOf(violations);
  }

  public List<FieldViolation> violations() {
    return violations;
  }
}
