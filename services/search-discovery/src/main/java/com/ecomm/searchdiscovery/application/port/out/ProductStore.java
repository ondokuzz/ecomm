package com.ecomm.searchdiscovery.application.port.out;

import com.ecomm.searchdiscovery.domain.Candidate;
import com.ecomm.searchdiscovery.domain.ListedProduct;
import com.ecomm.searchdiscovery.domain.SearchableProduct;
import java.util.List;
import java.util.Optional;

/** Search's copy of every Product Catalog has published, removed ones included. */
public interface ProductStore {

  Optional<SearchableProduct> find(String sku);

  /**
   * The Product with the SKU, with its Category's name and its Variants' Stock, unless it was
   * removed or never published.
   */
  Optional<ListedProduct> listed(String sku);

  /** Adds the Product, or replaces the one with its SKU. */
  void save(SearchableProduct product);

  /**
   * Every Product not removed that {@code text} matches, or every one when it is null, each with
   * whether any of its Variants is in Stock and how well it matched.
   */
  List<Candidate> candidates(String text);
}
