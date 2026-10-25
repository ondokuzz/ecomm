package com.ecomm.reviewsratings.application;

import com.ecomm.reviewsratings.application.port.in.ModerateReviewsUseCase;
import com.ecomm.reviewsratings.application.port.out.ReviewStore;
import com.ecomm.reviewsratings.application.port.out.TimeSource;
import com.ecomm.reviewsratings.domain.InvalidQueryException;
import com.ecomm.reviewsratings.domain.Review;
import com.ecomm.reviewsratings.domain.ReviewNotFoundException;
import com.ecomm.reviewsratings.domain.ReviewPage;
import java.util.function.UnaryOperator;

public class ModerationService implements ModerateReviewsUseCase {

  private final ReviewStore reviews;
  private final TimeSource time;

  public ModerationService(ReviewStore reviews, TimeSource time) {
    this.reviews = reviews;
    this.time = time;
  }

  @Override
  public ReviewPage queue(int page, int size) {
    if (page < 0) {
      throw new InvalidQueryException("The page counts from 0.");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidQueryException("A page holds from 1 to " + MAX_PAGE_SIZE + " reviews.");
    }
    return reviews.awaitingModeration(page, size);
  }

  @Override
  public Review review(String reviewId) {
    return reviews.find(reviewId).orElseThrow(() -> new ReviewNotFoundException(reviewId));
  }

  @Override
  public Review publish(String reviewId, String staffId) {
    var at = time.now();
    return moderated(reviewId, review -> review.publish(staffId, at));
  }

  @Override
  public Review dismissReports(String reviewId, String staffId) {
    var at = time.now();
    return moderated(reviewId, review -> review.dismissReports(staffId, at));
  }

  @Override
  public Review remove(String reviewId, String staffId, String reason) {
    var at = time.now();
    return moderated(reviewId, review -> review.remove(staffId, reason, at));
  }

  private Review moderated(String reviewId, UnaryOperator<Review> action) {
    return reviews
        .update(reviewId, action)
        .orElseThrow(() -> new ReviewNotFoundException(reviewId));
  }
}
