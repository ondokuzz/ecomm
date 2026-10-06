package com.ecomm.reviewsratings.domain;

/** A request to read reviews or ratings that asks for something it may not, such as 51 SKUs. */
public class InvalidQueryException extends RuntimeException {

  public InvalidQueryException(String message) {
    super(message);
  }
}
