package com.ecomm.checkoutpricing.application.port.in;

/** Checkout couldn't get its own token from Keycloak, so it can't call its internal services. */
public class ServiceTokenUnavailableException extends RuntimeException {

  public ServiceTokenUnavailableException(Throwable cause) {
    super("Checkout's service token is unavailable.", cause);
  }
}
