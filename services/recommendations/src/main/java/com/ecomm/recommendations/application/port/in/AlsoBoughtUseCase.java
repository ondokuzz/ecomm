package com.ecomm.recommendations.application.port.in;

import com.ecomm.recommendations.domain.InvalidQueryException;
import com.ecomm.recommendations.domain.Recommendation;
import java.util.List;

/** What Customers also bought with a Product. */
public interface AlsoBoughtUseCase {

  int DEFAULT_LIMIT = 4;
  int MAX_LIMIT = 12;

  /**
   * The other Products in counting Orders that bought any of {@code sku}'s Variants, ranked by how
   * many such Orders bought them, then by the most recently placed of those Orders, then by SKU;
   * removed Products are left out. An unknown SKU has none.
   *
   * @throws InvalidQueryException if {@code limit} isn't from 1 to {@link #MAX_LIMIT}
   */
  List<Recommendation> alsoBought(String sku, int limit);
}
