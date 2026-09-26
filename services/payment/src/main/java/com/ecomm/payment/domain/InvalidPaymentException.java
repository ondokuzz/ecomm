package com.ecomm.payment.domain;

/** A payment was asked for with missing or malformed data. */
public class InvalidPaymentException extends RuntimeException {

  public InvalidPaymentException(String message) {
    super(message);
  }
}
