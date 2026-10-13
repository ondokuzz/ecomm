package com.ecomm.fulfillmentshipping.adapter.in.web;

/** A quote request with a field that's missing or malformed, named in {@link #violation()}. */
class InvalidQuoteRequestException extends RuntimeException {

  record Violation(String field, String message) {}

  private final Violation violation;

  InvalidQuoteRequestException(String field, String message) {
    super(field + " " + message);
    this.violation = new Violation(field, message);
  }

  Violation violation() {
    return violation;
  }
}
