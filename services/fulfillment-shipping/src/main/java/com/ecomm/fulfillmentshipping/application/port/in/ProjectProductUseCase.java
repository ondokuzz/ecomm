package com.ecomm.fulfillmentshipping.application.port.in;

import com.ecomm.fulfillmentshipping.domain.Product;

public interface ProjectProductUseCase {

  /**
   * Keeps {@code product} if it is newer than the version held.
   *
   * @return whether it was applied, rather than ignored as a duplicate or stale
   */
  boolean apply(Product product);
}
