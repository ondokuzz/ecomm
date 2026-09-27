package com.ecomm.ordermanagement.application.port.in;

import java.util.UUID;

/** An Order's status changed between reading it and writing the new one. */
public class ConcurrentStatusChangeException extends RuntimeException {

  public ConcurrentStatusChangeException(UUID id) {
    super("order " + id + " changed status concurrently; read it and try again");
  }
}
