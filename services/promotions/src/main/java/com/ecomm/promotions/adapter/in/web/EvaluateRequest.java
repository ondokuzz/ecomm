package com.ecomm.promotions.adapter.in.web;

import com.ecomm.promotions.domain.InvalidPromotionException;
import com.ecomm.promotions.domain.Line;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A Checkout Session's lines and the Coupon code applied to it, if any: {@code {"lines":
 * [{"variantId", "sku", "category", "quantity", "unitPrice": {"amountMinor", "currency"}}],
 * "couponCode"}}. The values are taken raw so that {@code 1.5} or {@code "2"} are rejected rather
 * than coerced.
 */
record EvaluateRequest(List<LineBody> lines, Object couponCode) {

  record LineBody(
      Object variantId, Object sku, Object category, Object quantity, Amount unitPrice) {

    Line toLine(String field) {
      if (!(quantity instanceof Integer count)) {
        throw new InvalidPromotionException(field + ".quantity", "must be an integer");
      }
      try {
        return new Line(
            text(variantId, field + ".variantId"),
            text(sku, field + ".sku"),
            text(category, field + ".category"),
            count,
            unitPrice == null ? null : unitPrice.toMoney(field + ".unitPrice"));
      } catch (InvalidPromotionException e) {
        // The Line names its own fields; put them in their place in the request.
        if (e.violation().field().startsWith(field)) {
          throw e;
        }
        throw new InvalidPromotionException(
            field + "." + e.violation().field(), e.violation().message());
      }
    }

    private static String text(Object value, String field) {
      if (value != null && !(value instanceof String)) {
        throw new InvalidPromotionException(field, "must be a string");
      }
      return (String) value;
    }
  }

  List<Line> toLines() {
    if (lines == null || lines.isEmpty()) {
      throw new InvalidPromotionException("lines", "need at least one line");
    }
    var converted = new ArrayList<Line>();
    for (var i = 0; i < lines.size(); i++) {
      var field = "lines[" + i + "]";
      if (lines.get(i) == null) {
        throw new InvalidPromotionException(field, "can't be null");
      }
      converted.add(lines.get(i).toLine(field));
    }
    return converted;
  }

  Optional<String> toCouponCode() {
    if (couponCode == null) {
      return Optional.empty();
    }
    if (!(couponCode instanceof String code) || code.isBlank()) {
      throw new InvalidPromotionException("couponCode", "must be a non-blank string");
    }
    return Optional.of(code);
  }
}
