package com.ecomm.catalog.domain;

import com.ecomm.commons.money.Money;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A sellable item in the Catalog, identified by its SKU, and sold as one or more Variants, each at
 * its own Price. Its {@code description}, free text for Customers to read, is optional: null when
 * it has none. Its attributes and its Variants' axis values must satisfy its Category's definitions
 * whenever it is written; see {@link Category#violations(Product)}.
 */
public record Product(
    String sku,
    String name,
    String description,
    String category,
    Map<String, String> attributes,
    List<String> images,
    List<Variant> variants) {

  public Product {
    requireText(sku, "sku");
    requireText(name, "name");
    description = description == null || description.isBlank() ? null : description;
    requireText(category, "category");
    if (!Category.isSlug(category)) {
      throw new InvalidProductException(
          "category must be lowercase letters, digits and hyphens, e.g. 'phones'");
    }
    attributes = attributes == null ? Map.of() : copyOfAttributes(attributes);
    images = copyOfImages(images);
    if (variants == null || variants.isEmpty()) {
      throw new InvalidProductException("a Product needs at least one Variant");
    }
    if (variants.stream().anyMatch(v -> v == null)) {
      throw new InvalidProductException("variants must not be null");
    }
    variants = List.copyOf(variants);
    var ids = new HashSet<String>();
    for (var variant : variants) {
      if (!ids.add(variant.id())) {
        throw new InvalidProductException("Variant ID " + variant.id() + " is listed twice");
      }
    }
    if (variants.stream().map(v -> v.price().currency()).distinct().count() > 1) {
      throw new InvalidProductException("a Product's Variants must all be priced in one currency");
    }
  }

  /** The lowest Price of any of its Variants. */
  public Money priceFrom() {
    return variants.stream()
        .map(Variant::price)
        .min(Comparator.comparingLong(Money::amountMinor))
        .orElseThrow();
  }

  public Optional<Variant> variant(String id) {
    return variants.stream().filter(v -> v.id().equals(id)).findFirst();
  }

  public List<String> variantIds() {
    return variants.stream().map(Variant::id).toList();
  }

  /** This Product with each Variant's axis values in {@code order}. */
  Product withAxisValuesIn(List<String> order) {
    return new Product(
        sku,
        name,
        description,
        category,
        attributes,
        images,
        variants.stream().map(v -> v.withAxisValuesIn(order)).toList());
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new InvalidProductException(field + " is required");
    }
  }

  private static Map<String, String> copyOfAttributes(Map<String, String> attributes) {
    attributes.forEach(
        (key, value) -> {
          if (key == null || key.isBlank() || value == null) {
            throw new InvalidProductException("attributes need a name and a value");
          }
        });
    return Map.copyOf(attributes);
  }

  static List<String> copyOfImages(List<String> images) {
    if (images == null) {
      return List.of();
    }
    if (images.stream().anyMatch(image -> image == null || image.isBlank())) {
      throw new InvalidProductException("images must not be blank");
    }
    return List.copyOf(images);
  }
}
