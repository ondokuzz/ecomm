package com.ecomm.recommendations.application;

import com.ecomm.recommendations.application.port.in.AlsoBoughtUseCase;
import com.ecomm.recommendations.application.port.out.Candidates;
import com.ecomm.recommendations.application.port.out.TimeSource;
import com.ecomm.recommendations.domain.Category;
import com.ecomm.recommendations.domain.InvalidQueryException;
import com.ecomm.recommendations.domain.Recommendation;
import com.ecomm.recommendations.domain.Recommendations;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Works out the Recommendations when asked, from the Orders, Products, Categories and Stock held,
 * so the answer is right whatever order their events arrived in.
 */
public class AlsoBoughtService implements AlsoBoughtUseCase {

  private final Candidates candidates;
  private final TimeSource time;
  private final Duration lookback;

  public AlsoBoughtService(Candidates candidates, TimeSource time, Duration lookback) {
    this.candidates = candidates;
    this.time = time;
    this.lookback = lookback;
  }

  @Override
  public List<Recommendation> alsoBought(String sku, int limit) {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new InvalidQueryException("limit must be from 1 to " + MAX_LIMIT);
    }
    var since = time.now().minus(lookback);
    var boughtTogether = candidates.boughtWith(sku, since);
    // The Category's best sellers are only asked for when they may be needed.
    Optional<Category> category =
        boughtTogether.size() < limit ? candidates.categoryOf(sku) : Optional.empty();
    var bestSellers =
        category.map(c -> candidates.bestSellersIn(c.slug(), since)).orElse(List.of());
    return Recommendations.rank(sku, boughtTogether, category, bestSellers, limit);
  }
}
