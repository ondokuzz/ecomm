package com.ecomm.reviewsratings.application;

import com.ecomm.reviewsratings.application.port.in.ReadReviewsUseCase;
import com.ecomm.reviewsratings.application.port.out.ReviewStore;
import com.ecomm.reviewsratings.domain.InvalidQueryException;
import com.ecomm.reviewsratings.domain.RatingSummary;
import com.ecomm.reviewsratings.domain.ReviewPage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class ReadReviewsService implements ReadReviewsUseCase {

  private final ReviewStore reviews;

  public ReadReviewsService(ReviewStore reviews) {
    this.reviews = reviews;
  }

  @Override
  public ReviewPage reviews(String sku, int page, int size) {
    if (page < 0) {
      throw new InvalidQueryException("The page counts from 0.");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidQueryException("A page holds from 1 to " + MAX_PAGE_SIZE + " reviews.");
    }
    return reviews.page(sku, page, size);
  }

  @Override
  public RatingSummary summary(String sku) {
    return summaries(List.of(sku)).getFirst();
  }

  @Override
  public List<RatingSummary> summaries(List<String> skus) {
    var distinct = new LinkedHashSet<>(skus);
    if (distinct.isEmpty() || distinct.size() > MAX_SKUS) {
      throw new InvalidQueryException("Ask for 1 to " + MAX_SKUS + " SKUs at once.");
    }
    var counts = reviews.ratingCounts(distinct);
    return distinct.stream()
        .map(sku -> RatingSummary.of(sku, counts.getOrDefault(sku, Map.of())))
        .toList();
  }
}
