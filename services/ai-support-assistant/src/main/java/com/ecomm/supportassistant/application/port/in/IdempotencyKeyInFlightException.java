package com.ecomm.supportassistant.application.port.in;

/**
 * A message with this {@code Idempotency-Key} is still being answered: once it is, a repeat answers
 * its reply.
 */
public class IdempotencyKeyInFlightException extends RuntimeException {

  public IdempotencyKeyInFlightException() {
    super("A message with this Idempotency-Key is still being answered; send it again shortly.");
  }
}
