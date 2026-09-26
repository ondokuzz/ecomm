package com.ecomm.payment.domain;

import com.ecomm.commons.money.Money;

/** An Order's amount to authorize. Throws {@link InvalidPaymentException} unless both are valid. */
public record AuthorizationRequest(String orderId, Money amount) {

  /** The longest Order ID a Payment can hold. */
  public static final int MAX_ORDER_ID_LENGTH = 64;

  public AuthorizationRequest {
    if (orderId == null || orderId.isBlank()) {
      throw new InvalidPaymentException("a payment needs an orderId");
    }
    if (orderId.length() > MAX_ORDER_ID_LENGTH) {
      throw new InvalidPaymentException(
          "an orderId is at most " + MAX_ORDER_ID_LENGTH + " characters");
    }
    if (amount == null) {
      throw new InvalidPaymentException("a payment needs an amount");
    }
    if (amount.amountMinor() <= 0) {
      throw new InvalidPaymentException("a payment's amount must be positive");
    }
  }
}
