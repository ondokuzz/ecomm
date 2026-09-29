package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.domain.AttributeDefinition;
import com.ecomm.catalog.domain.CategorySummary;
import java.util.List;

/** A Category as clients see it: its definitions in order, and how many Products it has. */
record CategoryResponse(
    String slug, String name, long productCount, List<DefinitionResponse> attributes) {

  /** {@code values} is empty unless {@code type} is {@code ENUM}. */
  record DefinitionResponse(
      String name, String type, List<String> values, boolean required, boolean variantAxis) {

    static DefinitionResponse of(AttributeDefinition d) {
      return new DefinitionResponse(
          d.name(), d.type().name(), d.values(), d.required(), d.variantAxis());
    }
  }

  static CategoryResponse of(CategorySummary summary) {
    var category = summary.category();
    return new CategoryResponse(
        category.slug(),
        category.name(),
        summary.productCount(),
        category.attributes().stream().map(DefinitionResponse::of).toList());
  }
}
