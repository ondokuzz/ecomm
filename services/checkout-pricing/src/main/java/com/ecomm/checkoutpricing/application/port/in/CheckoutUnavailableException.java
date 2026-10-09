package com.ecomm.checkoutpricing.application.port.in;

/** The checkout Saga couldn't be reached, so a payment couldn't be started or read. */
public class CheckoutUnavailableException extends RuntimeException {

  public CheckoutUnavailableException(Throwable cause) {
    super("Checkout can't take payments right now.", cause);
  }
}
