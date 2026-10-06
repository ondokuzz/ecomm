package com.ecomm.searchdiscovery.application.port.out;

import com.ecomm.searchdiscovery.domain.Candidate;
import com.ecomm.searchdiscovery.domain.SearchableProduct;
import java.util.List;
import java.util.Optional;

/** Search's copy of every Product Catalog has published, removed ones included. */
public interface ProductStore {

  Optional<SearchableProduct> find(String sku);

  /** Adds the Product, or replaces the one with its SKU. */
  void save(SearchableProduct product);

  /**
   * Every Product not removed that {@code text} matches, or every one when it is null, each with
   * whether any of its Variants is in Stock and how well it matched.
   */
  List<Candidate> candidates(String text);
}
