package com.ecomm.supportassistant.domain;

/** The Conversation has a message sent with this {@code Idempotency-Key}, and it said otherwise. */
public class IdempotencyKeyReusedException extends RuntimeException {

  public IdempotencyKeyReusedException() {
    super("This Idempotency-Key was used with a different message.");
  }
}
