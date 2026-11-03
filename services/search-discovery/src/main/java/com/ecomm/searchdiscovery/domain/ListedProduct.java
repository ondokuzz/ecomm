package com.ecomm.searchdiscovery.domain;

import java.util.Set;

/**
 * A Product not removed, with its Category's name, null when Search holds the Category removed or
 * not yet, and the IDs of its Variants in Stock: those Inventory stocks and has some of available.
 * One with no Stock event yet isn't.
 */
public record ListedProduct(
    SearchableProduct product, String categoryName, Set<String> variantsInStock) {

  public ListedProduct {
    variantsInStock = Set.copyOf(variantsInStock);
  }

  /** Its Category's name, or its slug while Search holds no name for it, as the facets list it. */
  public String categoryLabel() {
    return categoryName == null ? product.category() : categoryName;
  }

  public boolean inStock(SearchableVariant variant) {
    return variantsInStock.contains(variant.variantId());
  }
}
