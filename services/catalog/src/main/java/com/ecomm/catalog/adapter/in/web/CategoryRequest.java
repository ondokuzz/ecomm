package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.domain.AttributeDefinition;
import com.ecomm.catalog.domain.AttributeType;
import com.ecomm.catalog.domain.Category;
import com.ecomm.catalog.domain.InvalidCategoryException;
import java.util.ArrayList;
import java.util.List;

/** A Category as Staff send it. On update the slug comes from the path and may be left out here. */
record CategoryRequest(String slug, String name, List<DefinitionRequest> attributes) {

  /**
   * {@code required} and {@code variantAxis} are false when left out. {@code type} is read here
   * rather than by Jackson, so an unknown one is refused naming its field.
   */
  record DefinitionRequest(
      String name, String type, List<String> values, boolean required, boolean variantAxis) {

    AttributeDefinition toDefinition() {
      return new AttributeDefinition(
          name, AttributeType.named(type), values, required, variantAxis);
    }
  }

  Category toCategory() {
    return toCategory(slug);
  }

  Category toCategoryWithSlug(String pathSlug) {
    if (slug != null && !slug.equals(pathSlug)) {
      throw new InvalidCategoryException("slug", "must match the one in the path");
    }
    return toCategory(pathSlug);
  }

  private Category toCategory(String slug) {
    var definitions = new ArrayList<AttributeDefinition>();
    if (attributes != null) {
      for (var i = 0; i < attributes.size(); i++) {
        try {
          definitions.add(attributes.get(i).toDefinition());
        } catch (InvalidCategoryException e) {
          throw e.within("attributes[" + i + "]");
        }
      }
    }
    return new Category(slug, name, definitions);
  }
}
