package com.ecomm.inventory.application.port.out;

import com.ecomm.inventory.domain.Stock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockRepository {

  Optional<Stock> find(String variantId);

  /**
   * The stock of those Variants that exist, locked until the surrounding transaction ends, so no
   * concurrent decrement can change it in between. Always locks in the same order, so two
   * overlapping batches cannot deadlock.
   */
  List<Stock> lockAll(Collection<String> variantIds);

  /** Overwrites the quantities of existing Variants. */
  void updateAll(List<Stock> stock);
}
