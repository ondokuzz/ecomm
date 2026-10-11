package com.ecomm.inventory.application.port.in;

import com.ecomm.inventory.domain.Stock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Stock reads, served from the Stock cache and filled from Postgres on a miss. */
public interface ReadStockUseCase {

  Optional<Stock> stock(String variantId);

  /**
   * The Stock of each Variant Inventory stocks, in the order asked, each once; Variants it doesn't
   * stock are left out.
   */
  List<Stock> stocks(Collection<String> variantIds);
}
