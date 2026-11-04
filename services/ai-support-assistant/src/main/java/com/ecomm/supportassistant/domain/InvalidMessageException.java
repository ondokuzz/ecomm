package com.ecomm.supportassistant.domain;

/** A Customer's message is empty or too long. */
public class InvalidMessageException extends RuntimeException {

  public InvalidMessageException(String message) {
    super(message);
  }
}
