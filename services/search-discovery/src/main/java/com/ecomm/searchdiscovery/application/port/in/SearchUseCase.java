package com.ecomm.searchdiscovery.application.port.in;

import com.ecomm.searchdiscovery.domain.SearchRequest;
import com.ecomm.searchdiscovery.domain.SearchResult;

/** Customers search the Catalog: a page of matching Products, with facets to narrow it by. */
public interface SearchUseCase {

  SearchResult search(SearchRequest request);
}
