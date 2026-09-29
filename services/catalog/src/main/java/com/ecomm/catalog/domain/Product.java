package com.ecomm.catalog.domain;

import com.ecomm.commons.money.Money;
import java.util.List;
import java.util.Map;

/**
 * A sellable item in the Catalog, identified by its SKU. Catalog owns its Price. Its attributes
 * must satisfy its Category's definitions whenever it is written; see {@link Category#violations}.
 *
 * <p>For now every Product has exactly one implicit Variant whose ID is the SKU; see {@link
 * #variants()}.
 */
public record Product(
    String sku,
    String name,
    String category,
    Map<String, String> attributes,
    Money price,
    List<String> images) {

  public Product {
    requireText(sku, "sku");
    requireText(name, "name");
    requireText(category, "category");
    if (!Category.isSlug(category)) {
      throw new InvalidProductException(
          "category must be lowercase letters, digits and hyphens, e.g. 'phones'");
    }
    if (price == null) {
      throw new InvalidProductException("price is required");
    }
    if (price.amountMinor() < 0) {
      throw new InvalidProductException("price must not be negative");
    }
    attributes = attributes == null ? Map.of() : copyOfAttributes(attributes);
    images = images == null ? List.of() : copyOfImages(images);
  }

  /** The Product's Variants. Until multi-Variant Products arrive, the one Variant is the SKU. */
  public List<Variant> variants() {
    return List.of(new Variant(sku, price));
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

  private static List<String> copyOfImages(List<String> images) {
    if (images.stream().anyMatch(image -> image == null || image.isBlank())) {
      throw new InvalidProductException("images must not be blank");
    }
    return List.copyOf(images);
  }
}
