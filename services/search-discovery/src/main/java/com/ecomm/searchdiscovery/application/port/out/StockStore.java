package com.ecomm.searchdiscovery.application.port.out;

import com.ecomm.searchdiscovery.domain.VariantStock;
import java.util.Optional;

/** Search's copy of every Variant's Stock Inventory has published. */
public interface StockStore {

  Optional<VariantStock> find(String variantId);

  void save(VariantStock stock);
}
