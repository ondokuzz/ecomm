package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.domain.InvalidPromotionException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Reads raw JSON values, naming the field when one isn't what it should be. */
final class Fields {

  private Fields() {}

  /** An ISO 8601 instant, or null when there is none, for the domain to name. */
  static Instant instant(String field, Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof String text)) {
      throw new InvalidPromotionException(field, "must be an ISO 8601 instant");
    }
    try {
      return Instant.parse(text);
    } catch (DateTimeParseException e) {
      throw new InvalidPromotionException(
          field, "must be an ISO 8601 instant, such as 2026-01-01T00:00:00Z");
    }
  }

  static boolean bool(String field, Object value) {
    if (!(value instanceof Boolean b)) {
      throw new InvalidPromotionException(field, "must be true or false");
    }
    return b;
  }

  /** A string, or null when there is none, for the domain to name. */
  static String string(String field, Object value) {
    if (value != null && !(value instanceof String)) {
      throw new InvalidPromotionException(field, "must be a string");
    }
    return (String) value;
  }
}
