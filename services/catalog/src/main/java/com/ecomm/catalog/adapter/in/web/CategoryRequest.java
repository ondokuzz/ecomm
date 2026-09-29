package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.domain.AttributeDefinition;
import com.ecomm.catalog.domain.AttributeType;
import com.ecomm.catalog.domain.Category;
import com.ecomm.catalog.domain.InvalidCategoryException;
import java.util.List;

/** A Category as Staff send it. On update the slug comes from the path and may be left out here. */
record CategoryRequest(String slug, String name, List<DefinitionRequest> attributes) {

  /** {@code required} and {@code variantAxis} are false when left out. */
  record DefinitionRequest(
      String name, AttributeType type, List<String> values, boolean required, boolean variantAxis) {

    AttributeDefinition toDefinition() {
      return new AttributeDefinition(name, type, values, required, variantAxis);
    }
  }

  Category toCategory() {
    return toCategory(slug);
  }

  Category toCategoryWithSlug(String pathSlug) {
    if (slug != null && !slug.equals(pathSlug)) {
      throw new InvalidCategoryException("slug in the body must match the one in the path");
    }
    return toCategory(pathSlug);
  }

  private Category toCategory(String slug) {
    var definitions =
        attributes == null
            ? List.<AttributeDefinition>of()
            : attributes.stream().map(DefinitionRequest::toDefinition).toList();
    return new Category(slug, name, definitions);
  }
}
