package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.commons.money.Money;
import java.util.Optional;

/** Catalog's current Prices. */
public interface CatalogPort {

  /** The Price Catalog holds now for this Variant; empty when it has none. */
  Optional<Money> price(String variantId);
}
