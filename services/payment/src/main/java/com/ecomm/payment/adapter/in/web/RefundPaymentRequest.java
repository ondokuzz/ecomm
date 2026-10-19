package com.ecomm.payment.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.payment.domain.InvalidPaymentException;
import java.util.Currency;
import java.util.Map;

/**
 * Names the Customer whose Payment is refunded, how much, and what the Refund is for: {@code
 * {"customerId", "amount": {"amountMinor", "currency"}, "reference"}}. Taken raw so that a wrong
 * type is rejected rather than coerced.
 */
record RefundPaymentRequest(Object customerId, Object amount, Object reference) {

  /** As long as the column that keeps it. */
  private static final int MAX_REFERENCE = 64;

  String customer() {
    if (!(customerId instanceof String id) || id.isBlank()) {
      throw new InvalidPaymentException("a Refund needs a customerId");
    }
    return id;
  }

  Money money() {
    if (!(amount instanceof Map<?, ?> fields)
        || !(fields.get("amountMinor") instanceof Number minor)
        || !(minor instanceof Integer || minor instanceof Long)
        || minor.longValue() <= 0
        || !(fields.get("currency") instanceof String code)) {
      throw new InvalidPaymentException(
          "a Refund needs an amount with a positive whole amountMinor and a currency");
    }
    try {
      return new Money(minor.longValue(), Currency.getInstance(code));
    } catch (IllegalArgumentException e) {
      throw new InvalidPaymentException("a Refund's currency must be an ISO 4217 code");
    }
  }

  String refundFor() {
    if (!(reference instanceof String text) || text.isBlank() || text.length() > MAX_REFERENCE) {
      throw new InvalidPaymentException(
          "a Refund needs a reference of up to " + MAX_REFERENCE + " characters");
    }
    return text;
  }
}
