package com.ecomm.catalog.domain;

/** A Product was described with missing or malformed data. */
public class InvalidProductException extends RuntimeException {

  public InvalidProductException(String message) {
    super(message);
  }
}
