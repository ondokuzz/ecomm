package com.ecomm.searchdiscovery.domain;

/** A search asked for something that can't match as written, such as a range upside down. */
public class InvalidSearchException extends RuntimeException {

  public InvalidSearchException(String message) {
    super(message);
  }
}
