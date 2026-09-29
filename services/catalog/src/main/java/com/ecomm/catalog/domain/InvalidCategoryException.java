package com.ecomm.catalog.domain;

/** A Category was described with missing or malformed data. */
public class InvalidCategoryException extends RuntimeException {

  public InvalidCategoryException(String message) {
    super(message);
  }
}
