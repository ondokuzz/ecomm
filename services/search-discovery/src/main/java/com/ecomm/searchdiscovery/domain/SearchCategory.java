package com.ecomm.searchdiscovery.domain;

import java.util.List;

/**
 * A Category as Search holds it: Catalog's last published snapshot of it, at {@code version}. Its
 * Attribute definitions decide the attribute facets offered once it is chosen. A removed one is
 * kept, marked {@code removed}, so a stale event can't bring it back.
 */
public record SearchCategory(
    String slug, long version, String name, List<AttributeDefinition> attributes, boolean removed) {

  public SearchCategory {
    attributes = List.copyOf(attributes);
  }
}
