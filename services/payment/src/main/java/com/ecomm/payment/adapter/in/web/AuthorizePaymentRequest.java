package com.ecomm.payment.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.payment.domain.AuthorizationRequest;
import com.ecomm.payment.domain.InvalidPaymentException;

/**
 * An Order's amount to authorize for a Customer: {@code {"customerId", "orderId", "amount":
 * {"amountMinor", "currency"}}}. The values are taken raw so that {@code 1.5} or {@code "7990"} are
 * rejected rather than coerced.
 */
record AuthorizePaymentRequest(Object customerId, Object orderId, Amount amount) {

  record Amount(Object amountMinor, Object currency) {

    Money toMoney() {
      if (!(amountMinor instanceof Integer || amountMinor instanceof Long)) {
        throw new InvalidPaymentException("amountMinor must be an integer in the minor unit");
      }
      if (!(currency instanceof String code)) {
        throw new InvalidPaymentException("currency must be an ISO 4217 code");
      }
      try {
        return Money.of(((Number) amountMinor).longValue(), code);
      } catch (IllegalArgumentException e) {
        throw new InvalidPaymentException("unknown currency " + code);
      }
    }
  }

  AuthorizationRequest toAuthorizationRequest() {
    if (customerId != null && !(customerId instanceof String)) {
      throw new InvalidPaymentException("customerId must be a string");
    }
    if (orderId != null && !(orderId instanceof String)) {
      throw new InvalidPaymentException("orderId must be a string");
    }
    return new AuthorizationRequest(
        (String) customerId, (String) orderId, amount == null ? null : amount.toMoney());
  }
}
