package com.ecomm.commons.idempotency;

import org.springframework.http.HttpStatus;

/** A request refused for its {@code Idempotency-Key}, with the {@code reason} a client reads. */
class IdempotencyKeyException extends RuntimeException {

  private final HttpStatus status;
  private final String reason;

  private IdempotencyKeyException(HttpStatus status, String reason, String message) {
    super(message);
    this.status = status;
    this.reason = reason;
  }

  static IdempotencyKeyException required() {
    return new IdempotencyKeyException(
        HttpStatus.BAD_REQUEST,
        "idempotencyKeyRequired",
        "This command requires an Idempotency-Key header.");
  }

  static IdempotencyKeyException invalid() {
    return new IdempotencyKeyException(
        HttpStatus.BAD_REQUEST,
        "idempotencyKeyInvalid",
        "An Idempotency-Key is 1 to 255 printable ASCII characters.");
  }

  static IdempotencyKeyException reused() {
    return new IdempotencyKeyException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "idempotencyKeyReused",
        "This Idempotency-Key was used with a different request.");
  }

  HttpStatus status() {
    return status;
  }

  String reason() {
    return reason;
  }
}
