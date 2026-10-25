package com.ecomm.reviewsratings.application.port.in;

import com.ecomm.reviewsratings.domain.Eligibility;
import com.ecomm.reviewsratings.domain.Report;
import com.ecomm.reviewsratings.domain.ReportReason;
import com.ecomm.reviewsratings.domain.Review;
import com.ecomm.reviewsratings.domain.ReviewContent;
import java.util.Optional;

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

  /**
   * The Customer's report of a review, and whether this request made it, rather than an earlier.
   */
  record Reported(Report report, boolean created) {}

  /**
   * Reports another Customer's published review. A Customer reports a review once: asking again
   * answers their first report, whatever this one says. A report never hides a review by itself.
   *
   * @throws com.ecomm.reviewsratings.domain.ReviewRefusedException if the review is theirs
   * @throws com.ecomm.reviewsratings.domain.ReviewNotFoundException if there is no such published
   *     review
   */
  Reported report(String customerId, String reviewId, ReportReason reason, Optional<String> note);
}
