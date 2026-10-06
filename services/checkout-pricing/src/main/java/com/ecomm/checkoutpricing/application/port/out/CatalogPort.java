package com.ecomm.checkoutpricing.application.port.out;

import com.ecomm.commons.money.Money;
import java.util.Optional;

/** Catalog's current Prices, and the Product each Variant belongs to. */
public interface CatalogPort {

  /** A Variant's Product, by its SKU and Category, and the Price Catalog holds for it now. */
  record PricedVariant(String sku, String category, Money price) {}

  /** The Variant with this ID; empty when Catalog has none, or holds no Price for it. */
  Optional<PricedVariant> variant(String variantId);
}
