package com.ecomm.recommendations.domain;

/** A request for Recommendations that asks for something it may not, such as 13 of them. */
public class InvalidQueryException extends RuntimeException {

  public InvalidQueryException(String message) {
    super(message);
  }
}
