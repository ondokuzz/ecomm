package com.ecomm.reviewsratings.application.port.out;

import com.ecomm.reviewsratings.domain.Review;
import com.ecomm.reviewsratings.domain.ReviewPage;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

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

  void replace(Review review);

  void delete(String reviewId);

  /** A page of the Product's reviews, newest first. */
  ReviewPage page(String sku, int page, int size);

  /** For each SKU with reviews, how many gave each rating; a rating none gave may be missing. */
  Map<String, Map<Integer, Long>> ratingCounts(Collection<String> skus);
}
