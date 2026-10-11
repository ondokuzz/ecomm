package com.ecomm.recommendations.application;

import com.ecomm.recommendations.application.port.in.AlsoBoughtUseCase;
import com.ecomm.recommendations.application.port.out.BoughtTogether;
import com.ecomm.recommendations.domain.InvalidQueryException;
import com.ecomm.recommendations.domain.Recommendation;
import java.util.List;

/**
 * Works out what was bought together when asked, from the Orders and Products held, so the answer
 * is right whatever order their events arrived in.
 */
public class AlsoBoughtService implements AlsoBoughtUseCase {

  private final BoughtTogether boughtTogether;

  public AlsoBoughtService(BoughtTogether boughtTogether) {
    this.boughtTogether = boughtTogether;
  }

  @Override
  public List<Recommendation> alsoBought(String sku, int limit) {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new InvalidQueryException("limit must be from 1 to " + MAX_LIMIT);
    }
    return boughtTogether.with(sku, limit);
  }
}
