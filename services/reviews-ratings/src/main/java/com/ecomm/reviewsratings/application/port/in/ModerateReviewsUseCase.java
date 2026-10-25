package com.ecomm.reviewsratings.application.port.in;

import com.ecomm.reviewsratings.domain.Review;
import com.ecomm.reviewsratings.domain.ReviewPage;

/**
 * What Staff do with reviews: work the moderation queue of held reviews and published ones with
 * open reports. Each action adds an entry to the review's moderation history.
 */
public interface ModerateReviewsUseCase {

  /** The most reviews one page of the queue holds. */
  int MAX_PAGE_SIZE = 50;

  /** A page of the queue, oldest review first. */
  ReviewPage queue(int page, int size);

  /**
   * Any review, whatever its status.
   *
   * @throws com.ecomm.reviewsratings.domain.ReviewNotFoundException if there is none
   */
  Review review(String reviewId);

  /**
   * Publishes a held review as the Staff member, resolving its reports.
   *
   * @throws com.ecomm.reviewsratings.domain.ModerationRefusedException unless it is held
   * @throws com.ecomm.reviewsratings.domain.ReviewNotFoundException if there is none
   */
  Review publish(String reviewId, String staffId);

  /**
   * Resolves a published review's open reports as the Staff member.
   *
   * @throws com.ecomm.reviewsratings.domain.ModerationRefusedException unless it has one open
   * @throws com.ecomm.reviewsratings.domain.ReviewNotFoundException if there is none
   */
  Review dismissReports(String reviewId, String staffId);

  /**
   * Removes a held or published review as the Staff member, for a reason its author sees, resolving
   * its reports.
   *
   * @throws com.ecomm.reviewsratings.domain.InvalidReviewException for a missing or long reason
   * @throws com.ecomm.reviewsratings.domain.ModerationRefusedException if it is removed already
   * @throws com.ecomm.reviewsratings.domain.ReviewNotFoundException if there is none
   */
  Review remove(String reviewId, String staffId, String reason);
}
