package com.ecomm.searchdiscovery.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A Product as Search holds it: Catalog's last published snapshot of it, at {@code version}. A
 * removed Product is kept, marked {@code removed}, so a stale event can't bring it back, but it is
 * never found. {@code listedAt} is when it last became searchable, which "newest" sorts by.
 */
public record SearchableProduct(
    String sku,
    long version,
    String name,
    String description,
    String category,
    Map<String, String> attributes,
    List<String> images,
    List<SearchableVariant> variants,
    boolean removed,
    Instant listedAt) {

  public SearchableProduct {
    attributes = Map.copyOf(attributes);
    images = List.copyOf(images);
    variants = List.copyOf(variants);
    if (variants.isEmpty()) {
      throw new IllegalArgumentException("a Product has at least one Variant");
    }
  }

  /** The image a summary shows: the Product's first, or else its first Variant's own. */
  public Optional<String> image() {
    return images.stream()
        .findFirst()
        .or(() -> variants.stream().flatMap(v -> v.images().stream()).findFirst());
  }

  /** The lowest of its Variants' Prices, which are all in one Currency. */
  public Money priceFrom() {
    return variants.stream()
        .map(SearchableVariant::price)
        .min(Comparator.comparingLong(Money::amountMinor))
        .orElseThrow();
  }

  /** Whether its Variants' Prices differ, so a summary shows {@link #priceFrom()} as "from". */
  public boolean priceVaries() {
    return variants.stream().map(SearchableVariant::price).distinct().count() > 1;
  }

  /**
   * The values it has for an attribute: its own, or, for a Variant axis, every Variant's. A filter
   * on the attribute matches when any of them is chosen.
   */
  public Set<String> valuesOf(String attribute) {
    var values = new LinkedHashSet<String>();
    var own = attributes.get(attribute);
    if (own != null) {
      values.add(own);
    }
    for (var variant : variants) {
      var value = variant.axisValues().get(attribute);
      if (value != null) {
        values.add(value);
      }
    }
    return values;
  }

  /** Every attribute and axis value it has, which text search looks through. */
  public Set<String> attributeValues() {
    var values = new LinkedHashSet<>(attributes.values());
    variants.forEach(v -> values.addAll(v.axisValues().values()));
    return values;
  }
}
