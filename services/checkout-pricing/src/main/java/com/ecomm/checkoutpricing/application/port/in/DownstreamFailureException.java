package com.ecomm.checkoutpricing.application.port.in;

/** A service Checkout depends on failed or answered in a way Checkout can't act on. */
public class DownstreamFailureException extends RuntimeException {

  public DownstreamFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
