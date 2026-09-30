package com.ecomm.checkoutpricing.domain;

/**
 * The payment gateway declined the Customer's payment method, for a reason such as {@code
 * insufficient_funds}.
 */
public class PaymentDeclinedException extends RuntimeException {

  private final String reason;

  public PaymentDeclinedException(String reason) {
    super("The payment was declined: " + reason);
    this.reason = reason;
  }

  public String reason() {
    return reason;
  }
}
