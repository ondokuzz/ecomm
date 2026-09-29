package com.ecomm.catalog.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A Staff-managed grouping of Products, named by a lowercase slug, that defines the attributes its
 * Products carry. Changing the definitions never rewrites existing Products; the rules apply on
 * each Product's next write.
 */
public record Category(String slug, String name, List<AttributeDefinition> attributes) {

  private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

  public Category {
    if (!isSlug(slug)) {
      throw new InvalidCategoryException(
          "slug must be lowercase letters, digits and hyphens, e.g. 'phones'");
    }
    if (name == null || name.isBlank()) {
      throw new InvalidCategoryException("name is required");
    }
    attributes = attributes == null ? List.of() : List.copyOf(attributes);
    var names = new HashSet<String>();
    for (var definition : attributes) {
      if (!names.add(definition.name())) {
        throw new InvalidCategoryException("attribute " + definition.name() + " is defined twice");
      }
    }
  }

  /** Whether {@code value} is a well-formed Category slug. */
  public static boolean isSlug(String value) {
    return value != null && SLUG.matcher(value).matches();
  }

  /**
   * How a Product's {@code attributes} break this Category's non-axis definitions: missing required
   * ones, values of the wrong type or outside an {@code ENUM}, and ones nothing defines. An
   * attribute named after a Variant axis is let through unchecked; axis values belong on Variants.
   */
  public List<FieldViolation> violations(Map<String, String> attributes) {
    var violations = new ArrayList<FieldViolation>();
    for (var definition : this.attributes) {
      var value = attributes.get(definition.name());
      // Axis values belong on Variants, so a Product need not carry one; one it does is checked.
      var required = definition.required() && !definition.variantAxis();
      String problem;
      if (value == null) {
        problem = required ? "is required" : null;
      } else if (value.isBlank() && required) {
        problem = "is required";
      } else {
        problem = definition.problemWith(value);
      }
      if (problem != null) {
        violations.add(new FieldViolation("attributes." + definition.name(), problem));
      }
    }
    var defined =
        this.attributes.stream().map(AttributeDefinition::name).collect(Collectors.toSet());
    attributes.keySet().stream()
        .filter(attribute -> !defined.contains(attribute))
        .sorted()
        .forEach(
            attribute ->
                violations.add(
                    new FieldViolation(
                        "attributes." + attribute, "is not defined by Category " + slug)));
    return violations;
  }
}
