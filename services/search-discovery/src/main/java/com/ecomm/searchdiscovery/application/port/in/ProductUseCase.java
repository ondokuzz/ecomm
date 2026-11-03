package com.ecomm.searchdiscovery.application.port.in;

import com.ecomm.searchdiscovery.domain.ListedProduct;
import java.util.Optional;

/**
 * Anyone may read one Product, with whether each of its Variants is in Stock. It may lag Catalog
 * and Inventory by an event, as the projection does.
 */
public interface ProductUseCase {

  /** Empty for a SKU the projection doesn't hold, or holds removed. */
  Optional<ListedProduct> product(String sku);
}
