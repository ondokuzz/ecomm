package com.ecomm.catalog.domain;

import java.util.ArrayList;

/**
 * A manufacturer's warranty on a Product: how many months it lasts, from 1 to 120, and which
 * manufacturer gives it. It applies to all of a Product's Variants. A Product without one has none.
 */
public record WarrantyTerms(int months, String manufacturer) {

  public static final int MIN_MONTHS = 1;
  public static final int MAX_MONTHS = 120;
  public static final int MAX_MANUFACTURER_LENGTH = 100;

  public WarrantyTerms {
    manufacturer = manufacturer == null ? null : manufacturer.strip();
    var violations = new ArrayList<FieldViolation>();
    if (months < MIN_MONTHS || months > MAX_MONTHS) {
      violations.add(
          new FieldViolation(
              "warranty.months", "must be from " + MIN_MONTHS + " to " + MAX_MONTHS));
    }
    if (manufacturer == null || manufacturer.isEmpty()) {
      violations.add(new FieldViolation("warranty.manufacturer", "is required"));
    } else if (manufacturer.length() > MAX_MANUFACTURER_LENGTH) {
      violations.add(
          new FieldViolation(
              "warranty.manufacturer",
              "must be at most " + MAX_MANUFACTURER_LENGTH + " characters"));
    }
    if (!violations.isEmpty()) {
      throw InvalidProductException.of(violations);
    }
  }
}
