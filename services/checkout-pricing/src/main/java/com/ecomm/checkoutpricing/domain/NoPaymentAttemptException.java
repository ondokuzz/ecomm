package com.ecomm.checkoutpricing.domain;

/** The Customer has never paid a Checkout Session with this ID, or it isn't theirs. */
public class NoPaymentAttemptException extends RuntimeException {

  public NoPaymentAttemptException(String sessionId) {
    super("No payment of Checkout Session " + sessionId + " was found.");
  }
}
