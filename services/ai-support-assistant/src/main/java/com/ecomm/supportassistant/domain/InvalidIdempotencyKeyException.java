package com.ecomm.supportassistant.domain;

/** An {@code Idempotency-Key} is 1 to 255 printable ASCII characters, as every context takes it. */
public class InvalidIdempotencyKeyException extends RuntimeException {

  public InvalidIdempotencyKeyException() {
    super("An Idempotency-Key is 1 to 255 printable ASCII characters.");
  }
}
