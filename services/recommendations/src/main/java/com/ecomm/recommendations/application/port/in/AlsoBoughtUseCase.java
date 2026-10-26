package com.ecomm.recommendations.application.port.in;

import com.ecomm.recommendations.domain.InvalidQueryException;
import com.ecomm.recommendations.domain.Recommendation;
import java.util.List;

/** What Customers also bought with a Product, filled up with its Category's best sellers. */
public interface AlsoBoughtUseCase {

  int DEFAULT_LIMIT = 4;
  int MAX_LIMIT = 12;

  /**
   * Up to {@code limit} Recommendations for {@code sku}, as {@code Recommendations.rank} ranks
   * them: the other Products in the counting Orders within the lookback that bought any of its
   * Variants, then its Category's best sellers over the same lookback. A Product removed from the
   * Catalog, or with nothing in Stock, is left out. An unknown SKU has none.
   *
   * @throws InvalidQueryException if {@code limit} isn't from 1 to {@link #MAX_LIMIT}
   */
  List<Recommendation> alsoBought(String sku, int limit);
}
