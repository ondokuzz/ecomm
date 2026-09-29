package com.ecomm.catalog.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * What kind of value an attribute holds. Values travel as strings; the type says which ones fit.
 */
public enum AttributeType {
  /** Any text. */
  TEXT,
  /** A decimal, such as {@code 30} or {@code 7.5}. */
  NUMBER,
  /** {@code true} or {@code false}. */
  BOOLEAN,
  /** One of the definition's values. */
  ENUM;

  /** Why {@code value} doesn't fit this type, or null when it does. */
  String problemWith(String value, List<String> values) {
    return switch (this) {
      case TEXT -> null;
      case NUMBER -> isDecimal(value) ? null : "must be a number";
      case BOOLEAN ->
          value.equals("true") || value.equals("false") ? null : "must be true or false";
      case ENUM -> values.contains(value) ? null : "must be one of " + String.join(", ", values);
    };
  }

  private static boolean isDecimal(String value) {
    try {
      new BigDecimal(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
