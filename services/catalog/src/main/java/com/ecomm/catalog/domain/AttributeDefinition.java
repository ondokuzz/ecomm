package com.ecomm.catalog.domain;

import java.util.List;

/**
 * An attribute a Category's Products carry. A Variant axis ({@code variantAxis}) tells a Product's
 * Variants apart, such as color or storage; any other definition describes the Product itself. A
 * malformed one is refused naming its own field: {@code name}, {@code type} or {@code values}.
 */
public record AttributeDefinition(
    String name, AttributeType type, List<String> values, boolean required, boolean variantAxis) {

  public AttributeDefinition {
    if (name == null || name.isBlank()) {
      throw new InvalidCategoryException("name", "is required");
    }
    if (type == null) {
      throw new InvalidCategoryException("type", AttributeType.EXPECTED);
    }
    values = values == null ? List.of() : List.copyOf(values);
    if (type == AttributeType.ENUM && values.isEmpty()) {
      throw new InvalidCategoryException("values", "are required for an ENUM");
    }
    if (type != AttributeType.ENUM && !values.isEmpty()) {
      throw new InvalidCategoryException("values", "are only for an ENUM");
    }
    if (values.stream().anyMatch(String::isBlank)) {
      throw new InvalidCategoryException("values", "must not be blank");
    }
  }

  /** Why {@code value} doesn't fit this definition, or null when it does. */
  String problemWith(String value) {
    return type.problemWith(value, values);
  }
}
