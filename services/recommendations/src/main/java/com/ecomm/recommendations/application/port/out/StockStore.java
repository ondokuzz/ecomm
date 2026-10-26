package com.ecomm.recommendations.application.port.out;

import com.ecomm.recommendations.domain.VariantStock;
import java.util.Optional;

public interface StockStore {

  /** The version held of the Variant's Stock, if any. */
  Optional<Long> versionOf(String variantId);

  /** Replaces whatever is held of the Variant's Stock. */
  void save(VariantStock stock);
}
