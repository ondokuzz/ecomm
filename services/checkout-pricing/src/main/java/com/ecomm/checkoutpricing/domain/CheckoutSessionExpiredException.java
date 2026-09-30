package com.ecomm.checkoutpricing.domain;

/** The Checkout Session's hold ran out before it was paid; nothing was bought. */
public class CheckoutSessionExpiredException extends RuntimeException {

  public CheckoutSessionExpiredException(String sessionId) {
    super("The hold on checkout " + sessionId + " has expired; please start again.");
  }
}
