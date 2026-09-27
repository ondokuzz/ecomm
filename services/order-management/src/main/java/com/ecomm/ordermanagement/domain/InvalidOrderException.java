package com.ecomm.ordermanagement.domain;

/** An Order was placed or changed with missing or malformed data. */
public class InvalidOrderException extends RuntimeException {

  public InvalidOrderException(String message) {
    super(message);
  }
}
