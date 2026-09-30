package com.ecomm.payment.domain;

import com.ecomm.commons.money.Money;

/**
 * An Order's amount to authorize for a Customer, paid with a payment method: an opaque token the
 * gateway issued for the Customer's card. Throws {@link InvalidPaymentException} unless all four
 * are valid.
 */
public record AuthorizationRequest(
    String customerId, String orderId, String paymentMethod, Money amount) {

  /** The longest Customer ID a Payment can hold. */
  public static final int MAX_CUSTOMER_ID_LENGTH = 255;

  /** The longest Order ID a Payment can hold. */
  public static final int MAX_ORDER_ID_LENGTH = 64;

  /** The longest payment method token a gateway may be handed. */
  public static final int MAX_PAYMENT_METHOD_LENGTH = 255;

  public AuthorizationRequest {
    if (customerId == null || customerId.isBlank()) {
      throw new InvalidPaymentException("a payment needs a customerId");
    }
    if (customerId.length() > MAX_CUSTOMER_ID_LENGTH) {
      throw new InvalidPaymentException(
          "a customerId is at most " + MAX_CUSTOMER_ID_LENGTH + " characters");
    }
    if (orderId == null || orderId.isBlank()) {
      throw new InvalidPaymentException("a payment needs an orderId");
    }
    if (orderId.length() > MAX_ORDER_ID_LENGTH) {
      throw new InvalidPaymentException(
          "an orderId is at most " + MAX_ORDER_ID_LENGTH + " characters");
    }
    if (paymentMethod == null || paymentMethod.isBlank()) {
      throw new InvalidPaymentException("a payment needs a paymentMethod");
    }
    if (paymentMethod.length() > MAX_PAYMENT_METHOD_LENGTH) {
      throw new InvalidPaymentException(
          "a paymentMethod is at most " + MAX_PAYMENT_METHOD_LENGTH + " characters");
    }
    if (amount == null) {
      throw new InvalidPaymentException("a payment needs an amount");
    }
    if (amount.amountMinor() <= 0) {
      throw new InvalidPaymentException("a payment's amount must be positive");
    }
  }
}
