package com.ecomm.payment.domain;

/**
 * The payment gateway failed to answer, so nothing is known about the payment and nothing is
 * recorded.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {

  public PaymentGatewayUnavailableException(String message) {
    super(message);
  }
}
