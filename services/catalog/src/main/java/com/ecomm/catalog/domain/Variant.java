package com.ecomm.catalog.domain;

import com.ecomm.commons.money.Money;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A purchasable version of a Product, identified by a Variant ID that is unique across the Catalog
 * and never changes. Its axis values (such as {@code color} and {@code storage}) tell it apart from
 * its Product's other Variants; they must cover exactly its Category's Variant axes, which {@link
 * Category#violations(Product)} checks. Its {@code images}, when it has any, replace its Product's.
 */
public record Variant(String id, Map<String, String> axisValues, Money price, List<String> images) {

  public Variant {
    if (id == null || id.isBlank()) {
      throw new InvalidProductException("every Variant needs an id");
    }
    if (price == null) {
      throw new InvalidProductException("Variant " + id + " needs a price");
    }
    if (price.amountMinor() < 0) {
      throw new InvalidProductException("Variant " + id + " price must not be negative");
    }
    axisValues = axisValues == null ? Map.of() : copyOfAxisValues(id, axisValues);
    images = Product.copyOfImages(images);
  }

  /** This Variant with its axis values in {@code order}; names {@code order} leaves out follow. */
  Variant withAxisValuesIn(List<String> order) {
    var ordered = new LinkedHashMap<String, String>();
    order.stream()
        .filter(axisValues::containsKey)
        .forEach(axis -> ordered.put(axis, axisValues.get(axis)));
    ordered.putAll(axisValues);
    return new Variant(id, ordered, price, images);
  }

  // Ordered, so a Variant reads "Obsidian · 256 GB" the same way every time.
  private static Map<String, String> copyOfAxisValues(String id, Map<String, String> axisValues) {
    axisValues.forEach(
        (axis, value) -> {
          if (axis == null || axis.isBlank() || value == null) {
            throw new InvalidProductException(
                "Variant " + id + " axis values need a name and a value");
          }
        });
    return Collections.unmodifiableMap(new LinkedHashMap<>(axisValues));
  }
}
