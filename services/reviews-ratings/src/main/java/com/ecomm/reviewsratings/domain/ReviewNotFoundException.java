package com.ecomm.reviewsratings.domain;

/**
 * No review with that ID is there for the caller: to its author, another Customer's is not found;
 * to anyone else, one that isn't published is not found either.
 */
public class ReviewNotFoundException extends RuntimeException {

  public ReviewNotFoundException(String reviewId) {
    super("There is no review " + reviewId + ".");
  }
}
