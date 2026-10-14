package com.ecomm.fulfillmentshipping.adapter.in.web;

import com.ecomm.fulfillmentshipping.domain.Line;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What to quote: {@code {"destination": {..., "country"}, "lines": [{"variantId", "quantity"}],
 * "currency"}}. The destination is a Shipping address, which Checkout has already validated; only
 * its country decides the quote, so its other fields are ignored. The values are taken raw so that
 * {@code 1.5} or {@code "2"} are rejected rather than coerced.
 */
record QuoteRequest(Map<String, Object> destination, List<Object> lines, Object currency) {

  private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

  String toCountry() {
    if (destination == null) {
      throw new InvalidRequestException("destination", "is required");
    }
    if (!(destination.get("country") instanceof String country) || !COUNTRIES.contains(country)) {
      throw new InvalidRequestException(
          "destination.country", "must be an ISO 3166-1 alpha-2 code, such as DE");
    }
    return country;
  }

  List<Line> toLines() {
    if (lines == null || lines.isEmpty()) {
      throw new InvalidRequestException("lines", "need at least one line");
    }
    var converted = new ArrayList<Line>();
    for (var i = 0; i < lines.size(); i++) {
      var field = "lines[" + i + "]";
      if (!(lines.get(i) instanceof Map<?, ?> line)) {
        throw new InvalidRequestException(field, "must be an object");
      }
      if (!(line.get("variantId") instanceof String variantId) || variantId.isBlank()) {
        throw new InvalidRequestException(field + ".variantId", "must be a non-blank string");
      }
      if (!(line.get("quantity") instanceof Integer quantity) || quantity < 1) {
        throw new InvalidRequestException(field + ".quantity", "must be a positive integer");
      }
      converted.add(new Line(variantId, quantity));
    }
    return converted;
  }

  Currency toCurrency() {
    if (currency instanceof String code) {
      try {
        return Currency.getInstance(code);
      } catch (IllegalArgumentException e) {
        // Named below.
      }
    }
    throw new InvalidRequestException("currency", "must be an ISO 4217 code, such as EUR");
  }
}
