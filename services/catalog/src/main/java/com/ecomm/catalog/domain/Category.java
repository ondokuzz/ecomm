package com.ecomm.catalog.domain;

import java.util.ArrayList;
import java.util.HashMap;
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
          "slug", "must be lowercase letters, digits and hyphens, e.g. 'phones'");
    }
    if (name == null || name.isBlank()) {
      throw new InvalidCategoryException("name", "is required");
    }
    attributes = attributes == null ? List.of() : List.copyOf(attributes);
    var names = new HashSet<String>();
    for (var i = 0; i < attributes.size(); i++) {
      var attribute = attributes.get(i).name();
      if (!names.add(attribute)) {
        throw new InvalidCategoryException(
            "attributes[" + i + "].name", "repeats the attribute " + attribute);
      }
    }
  }

  /** Whether {@code value} is a well-formed Category slug. */
  public static boolean isSlug(String value) {
    return value != null && SLUG.matcher(value).matches();
  }

  /**
   * How {@code product} breaks this Category's definitions: its attributes, as {@link
   * #violations(Map)} checks them, then each Variant's axis values. A Variant must carry a value
   * for every Variant axis and nothing else, each fitting its axis's type, and no two Variants may
   * share axis values.
   */
  public List<FieldViolation> violations(Product product) {
    var violations = new ArrayList<>(violations(product.attributes()));
    var variants = product.variants();
    var seen = new HashMap<Map<String, String>, Integer>();
    for (var i = 0; i < variants.size(); i++) {
      var axisValues = variants.get(i).axisValues();
      var field = "variants[" + i + "].axisValues";
      var problems = axisViolations(field, axisValues);
      violations.addAll(problems);
      var twin = seen.putIfAbsent(axisValues, i);
      if (problems.isEmpty() && twin != null) {
        violations.add(
            new FieldViolation(field, "repeats the axis values of variants[" + twin + "]"));
      }
    }
    return violations;
  }

  /**
   * How a Product's {@code attributes} break this Category's non-axis definitions: missing required
   * ones, values of the wrong type or outside an {@code ENUM}, ones nothing defines, and ones named
   * after a Variant axis, whose values belong on each Variant instead.
   */
  public List<FieldViolation> violations(Map<String, String> attributes) {
    var violations = new ArrayList<FieldViolation>();
    for (var definition : this.attributes) {
      var value = attributes.get(definition.name());
      String problem;
      if (definition.variantAxis()) {
        problem = value == null ? null : "is a Variant axis; give each Variant its value instead";
      } else {
        problem = problemWith(definition, value);
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

  /**
   * {@code product} with each Variant's axis values in the order this Category defines its axes.
   */
  public Product arrange(Product product) {
    return product.withAxisValuesIn(axes().stream().map(AttributeDefinition::name).toList());
  }

  private List<AttributeDefinition> axes() {
    return attributes.stream().filter(AttributeDefinition::variantAxis).toList();
  }

  private List<FieldViolation> axisViolations(String field, Map<String, String> axisValues) {
    var violations = new ArrayList<FieldViolation>();
    var axes = axes();
    for (var axis : axes) {
      // Every axis is required of a Variant, whatever its definition says.
      var value = axisValues.get(axis.name());
      var problem = value == null || value.isBlank() ? "is required" : axis.problemWith(value);
      if (problem != null) {
        violations.add(new FieldViolation(field + "." + axis.name(), problem));
      }
    }
    var axisNames = axes.stream().map(AttributeDefinition::name).collect(Collectors.toSet());
    axisValues.keySet().stream()
        .filter(name -> !axisNames.contains(name))
        .sorted()
        .forEach(
            name ->
                violations.add(
                    new FieldViolation(
                        field + "." + name, "is not a Variant axis of Category " + slug)));
    return violations;
  }

  /** Why a non-axis attribute's {@code value} (null when missing) breaks {@code definition}. */
  private static String problemWith(AttributeDefinition definition, String value) {
    if (value == null) {
      return definition.required() ? "is required" : null;
    }
    if (value.isBlank() && definition.required()) {
      return "is required";
    }
    return definition.problemWith(value);
  }
}
