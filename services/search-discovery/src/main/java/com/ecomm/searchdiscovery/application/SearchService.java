package com.ecomm.searchdiscovery.application;

import com.ecomm.searchdiscovery.application.port.in.SearchUseCase;
import com.ecomm.searchdiscovery.application.port.out.CategoryStore;
import com.ecomm.searchdiscovery.application.port.out.ProductStore;
import com.ecomm.searchdiscovery.domain.Search;
import com.ecomm.searchdiscovery.domain.SearchRequest;
import com.ecomm.searchdiscovery.domain.SearchResult;

/**
 * The text index finds the candidates; filtering, ordering, paging and facet counting happen over
 * them in {@link Search} (ADR 0001).
 */
public class SearchService implements SearchUseCase {

  private final ProductStore products;
  private final CategoryStore categories;

  public SearchService(ProductStore products, CategoryStore categories) {
    this.products = products;
    this.categories = categories;
  }

  @Override
  public SearchResult search(SearchRequest request) {
    return Search.run(request, products.candidates(request.text()), categories.listed());
  }
}
