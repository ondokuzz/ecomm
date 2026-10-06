package com.ecomm.searchdiscovery.domain;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a Customer searches for. Every filter is optional, and a Product must match all of them:
 *
 * <ul>
 *   <li>{@code text}: words to look for in the name, the description and the attribute values;
 *   <li>{@code category}: a Category's slug;
 *   <li>{@code values}: for an ENUM, TEXT or BOOLEAN attribute, the values any of which it must
 *       have, its own or any Variant's;
 *   <li>{@code ranges}: for a NUMBER attribute, the range a value of it must lie in;
 *   <li>{@code price}: a range any Variant's Price must lie in;
 *   <li>{@code inStockOnly}: whether any of its Variants must be in Stock.
 * </ul>
 *
 * {@code page} counts from 0 and holds {@code size} Products.
 */
public record SearchRequest(
    String text,
    String category,
    Map<String, Set<String>> values,
    Map<String, NumberRange> ranges,
    PriceRange price,
    boolean inStockOnly,
    SortOrder sort,
    int page,
    int size) {

  /** The most Products a page can hold. */
  public static final int MAX_PAGE_SIZE = 100;

  public SearchRequest {
    text = text == null || text.isBlank() ? null : text.strip();
    category = category == null || category.isBlank() ? null : category;
    values =
        values.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
    ranges = Map.copyOf(ranges);
    if (sort == null) {
      sort = SortOrder.RELEVANCE;
    }
    if (page < 0) {
      throw new InvalidSearchException("page counts from 0");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidSearchException("size is 1 to " + MAX_PAGE_SIZE);
    }
  }

  boolean matches(Candidate candidate) {
    return matchesCategory(candidate)
        && matchesAttributes(candidate, null)
        && matchesPrice(candidate)
        && matchesStock(candidate);
  }

  boolean matchesCategory(Candidate candidate) {
    return category == null || category.equals(candidate.product().category());
  }

  /** Whether it matches every attribute filter but {@code except}'s, which may be null. */
  boolean matchesAttributes(Candidate candidate, String except) {
    var product = candidate.product();
    for (var filter : values.entrySet()) {
      if (!filter.getKey().equals(except)
          && product.valuesOf(filter.getKey()).stream().noneMatch(filter.getValue()::contains)) {
        return false;
      }
    }
    for (var filter : ranges.entrySet()) {
      if (!filter.getKey().equals(except)
          && product.valuesOf(filter.getKey()).stream().noneMatch(filter.getValue()::contains)) {
        return false;
      }
    }
    return true;
  }

  boolean matchesPrice(Candidate candidate) {
    return price == null || price.contains(candidate.product());
  }

  boolean matchesStock(Candidate candidate) {
    return !inStockOnly || candidate.inStock();
  }
}
