package com.ecomm.reviewsratings.domain;

/** No review with that ID is the Customer's; another Customer's is not found either. */
public class ReviewNotFoundException extends RuntimeException {

  public ReviewNotFoundException(String reviewId) {
    super("You have no review " + reviewId + ".");
  }
}
