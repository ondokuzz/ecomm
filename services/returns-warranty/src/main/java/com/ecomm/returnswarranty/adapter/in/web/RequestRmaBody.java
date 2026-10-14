package com.ecomm.returnswarranty.adapter.in.web;

import com.ecomm.returnswarranty.domain.RmaKind;
import com.ecomm.returnswarranty.domain.RmaRequest;
import java.util.List;

/**
 * A Customer's RMA request: {@code {"orderId", "variantId", "quantity", "serialNumbers"?: [...],
 * "kind": "RETURN" | "WARRANTY_CLAIM", "reason"}}. Serial numbers are named, one per unit, for a
 * serial-tracked Variant, and left out or empty for any other.
 */
record RequestRmaBody(
    String orderId,
    String variantId,
    Integer quantity,
    List<String> serialNumbers,
    String kind,
    String reason) {

  /** The longest reason a Customer can give, and the longest note Staff can. */
  static final int MAX_TEXT = 1000;

  /**
   * @throws InvalidRequestException naming the first field that's missing or malformed
   */
  RmaRequest toRequest(String customerId) {
    required("orderId", orderId);
    required("variantId", variantId);
    if (quantity == null || quantity < 1) {
      throw new InvalidRequestException("quantity", "must be a positive integer");
    }
    var serials = serialNumbers == null ? List.<String>of() : serialNumbers;
    for (var i = 0; i < serials.size(); i++) {
      required("serialNumbers[" + i + "]", serials.get(i));
    }
    return new RmaRequest(
        customerId, orderId, variantId, quantity, serials, toKind(), text("reason", reason));
  }

  private RmaKind toKind() {
    try {
      return RmaKind.valueOf(kind);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new InvalidRequestException("kind", "must be RETURN or WARRANTY_CLAIM");
    }
  }

  /** A required text of 1 to {@value #MAX_TEXT} characters, not all blank. */
  static String text(String field, String value) {
    if (value == null || value.isBlank() || value.length() > MAX_TEXT) {
      throw new InvalidRequestException(field, "must be 1 to " + MAX_TEXT + " characters");
    }
    return value;
  }

  private static void required(String field, String value) {
    if (value == null || value.isBlank()) {
      throw new InvalidRequestException(field, "must be a non-blank string");
    }
  }
}
