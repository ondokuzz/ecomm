package com.ecomm.reviewsratings.application.port.out;

import com.ecomm.reviewsratings.domain.Review;
import com.ecomm.reviewsratings.domain.ReviewPage;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Every review, at most one per Customer and Product. */
public interface ReviewStore {

  Optional<Review> find(String reviewId);

  Optional<Review> find(String sku, String customerId);

  /**
   * Adds a new review.
   *
   * @return false, adding nothing, if the Customer has a review of the Product already
   */
  boolean add(Review review);

  /**
   * Applies {@code change} to the review as it is kept, and keeps what it returns. If another
   * change was kept since the review was read, {@code change} is applied again to the newer review,
   * so no change is lost. A change that returns the review it was given keeps nothing, and one that
   * throws keeps nothing either.
   *
   * @return the review as kept; empty if there is no such review
   */
  Optional<Review> update(String reviewId, UnaryOperator<Review> change);

  void delete(String reviewId);

  /** A page of the Product's reviews, newest first. */
  ReviewPage page(String sku, int page, int size);

  /** A page of the reviews that await moderation, oldest first. */
  ReviewPage awaitingModeration(int page, int size);

  /** For each SKU with reviews, how many gave each rating; a rating none gave may be missing. */
  Map<String, Map<Integer, Long>> ratingCounts(Collection<String> skus);
}
