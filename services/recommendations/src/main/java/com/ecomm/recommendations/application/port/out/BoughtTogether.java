package com.ecomm.recommendations.application.port.out;

import com.ecomm.recommendations.domain.Recommendation;
import java.util.List;

/** Works out, from the projection, which Products counting Orders bought together. */
public interface BoughtTogether {

  /** As {@code AlsoBoughtUseCase.alsoBought}, with {@code limit} already checked. */
  List<Recommendation> with(String sku, int limit);
}
