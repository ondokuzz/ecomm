package com.ecomm.reviewsratings.application.port.in;

import com.ecomm.reviewsratings.domain.Eligibility;
import com.ecomm.reviewsratings.domain.Review;
import com.ecomm.reviewsratings.domain.ReviewContent;

/**
 * What a Customer does with their own reviews. Whether they may review is checked when they post; a
 * posted review stays even if its Order is later cancelled.
 */
public interface CustomerReviewsUseCase {

  Eligibility eligibility(String customerId, String sku);

  /**
   * Posts the Customer's review of the Product under {@code author}, their display name.
   *
   * @throws com.ecomm.reviewsratings.domain.ReviewRefusedException if they haven't bought it, or
   *     have reviewed it already
   */
  Review post(String customerId, String author, String sku, ReviewContent content);

  /**
   * @throws com.ecomm.reviewsratings.domain.ReviewNotFoundException unless it is theirs
   */
  Review edit(String customerId, String reviewId, ReviewContent content);

  /**
   * @throws com.ecomm.reviewsratings.domain.ReviewNotFoundException unless it is theirs
   */
  void delete(String customerId, String reviewId);
}
