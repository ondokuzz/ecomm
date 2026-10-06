package com.ecomm.reviewsratings.adapter.in.web;

import com.ecomm.reviewsratings.domain.InvalidReviewException;
import com.ecomm.reviewsratings.domain.ReviewContent;

/** What a Customer posts or edits: {@code {"rating", "title", "body"}}, the title optional. */
record ReviewRequest(Integer rating, String title, String body) {

  ReviewContent toContent() {
    if (rating == null) {
      throw new InvalidReviewException(ReviewContent.INVALID_RATING);
    }
    return ReviewContent.of(rating, title, body);
  }
}
