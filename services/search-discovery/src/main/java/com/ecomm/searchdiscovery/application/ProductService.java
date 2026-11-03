package com.ecomm.searchdiscovery.application;

import com.ecomm.searchdiscovery.application.port.in.ProductUseCase;
import com.ecomm.searchdiscovery.application.port.out.ProductStore;
import com.ecomm.searchdiscovery.domain.ListedProduct;
import java.util.Optional;

/**
 * One Product from the projection, which already joins Catalog's Products with Inventory's Stock,
 * so one read answers what would otherwise take Catalog, then Inventory per Variant.
 */
public class ProductService implements ProductUseCase {

  private final ProductStore products;

  public ProductService(ProductStore products) {
    this.products = products;
  }

  @Override
  public Optional<ListedProduct> product(String sku) {
    return products.listed(sku);
  }
}
