package com.ecomm.checkoutpricing.domain;

/** No Checkout Session of the Customer's has this ID: it never existed, ended, or isn't theirs. */
public class CheckoutSessionNotFoundException extends RuntimeException {

  public CheckoutSessionNotFoundException(String sessionId) {
    super("No checkout " + sessionId + " is in progress.");
  }
}
