package com.ecomm.reviewsratings.application.port.in;

import com.ecomm.reviewsratings.domain.RatingSummary;
import com.ecomm.reviewsratings.domain.ReviewPage;
import java.util.List;

/** What anyone may read: a Product's reviews and rating summaries. */
public interface ReadReviewsUseCase {

  /** The most SKUs {@link #summaries} takes at once. */
  int MAX_SKUS = 50;

  /** The most reviews one page holds. */
  int MAX_PAGE_SIZE = 50;

  /** A page of the Product's reviews, newest first. */
  ReviewPage reviews(String sku, int page, int size);

  RatingSummary summary(String sku);

  /** A summary of each distinct SKU, in the order asked, from 1 to {@link #MAX_SKUS} of them. */
  List<RatingSummary> summaries(List<String> skus);
}
