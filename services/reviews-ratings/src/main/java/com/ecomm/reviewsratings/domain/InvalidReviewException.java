package com.ecomm.reviewsratings.domain;

/** A review that breaks a rule of what it may say; the message says which. */
public class InvalidReviewException extends RuntimeException {

  public InvalidReviewException(String message) {
    super(message);
  }
}
