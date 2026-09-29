package com.ecomm.catalog.domain;

import java.util.List;

/**
 * An attribute a Category's Products carry. A Variant axis ({@code variantAxis}) tells a Product's
 * Variants apart, such as color or storage; any other definition describes the Product itself.
 */
public record AttributeDefinition(
    String name, AttributeType type, List<String> values, boolean required, boolean variantAxis) {

  public AttributeDefinition {
    if (name == null || name.isBlank()) {
      throw new InvalidCategoryException("every attribute definition needs a name");
    }
    if (type == null) {
      throw new InvalidCategoryException(
          "attribute " + name + " needs a type: TEXT, NUMBER, BOOLEAN or ENUM");
    }
    values = values == null ? List.of() : List.copyOf(values);
    if (type == AttributeType.ENUM && values.isEmpty()) {
      throw new InvalidCategoryException("ENUM attribute " + name + " needs its values");
    }
    if (type != AttributeType.ENUM && !values.isEmpty()) {
      throw new InvalidCategoryException("only an ENUM attribute has values, not " + name);
    }
    if (values.stream().anyMatch(String::isBlank)) {
      throw new InvalidCategoryException("attribute " + name + " has a blank value");
    }
  }

  /** Why {@code value} doesn't fit this definition, or null when it does. */
  String problemWith(String value) {
    return type.problemWith(value, values);
  }
}
