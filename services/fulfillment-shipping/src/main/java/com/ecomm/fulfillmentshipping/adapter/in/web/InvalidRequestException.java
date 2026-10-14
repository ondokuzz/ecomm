package com.ecomm.fulfillmentshipping.adapter.in.web;

/** A request with a field that's missing or malformed, named in {@link #violation()}. */
class InvalidRequestException extends RuntimeException {

  record Violation(String field, String message) {}

  private final Violation violation;

  InvalidRequestException(String field, String message) {
    super(field + " " + message);
    this.violation = new Violation(field, message);
  }

  Violation violation() {
    return violation;
  }
}
