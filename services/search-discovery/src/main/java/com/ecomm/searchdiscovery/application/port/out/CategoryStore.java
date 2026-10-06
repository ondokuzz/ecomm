package com.ecomm.searchdiscovery.application.port.out;

import com.ecomm.searchdiscovery.domain.SearchCategory;
import java.util.List;
import java.util.Optional;

/** Search's copy of every Category Catalog has published, removed ones included. */
public interface CategoryStore {

  Optional<SearchCategory> find(String slug);

  void save(SearchCategory category);

  /** Every Category not removed. */
  List<SearchCategory> listed();
}
