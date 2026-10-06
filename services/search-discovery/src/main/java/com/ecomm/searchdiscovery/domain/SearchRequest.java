package com.ecomm.searchdiscovery.domain;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
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

  /** Whether it matches every filter, as a result must. */
  boolean matches(Candidate candidate) {
    return matchesCategory(candidate)
        && matchesAttributesBut(candidate, null)
        && matchesPrice(candidate)
        && matchesStock(candidate);
  }

  // Each facet is counted over the candidates matching every filter but its own (disjunctive
  // counting).

  /**
   * Whether it counts toward the Category facet: every filter but the Category, and, once one is
   * chosen, its attribute filters, which choosing another Category drops.
   */
  boolean countsForCategories(Candidate candidate) {
    return (category != null || matchesAttributesBut(candidate, null))
        && matchesPrice(candidate)
        && matchesStock(candidate);
  }

  /** Whether it counts toward {@code attribute}'s facet: every filter but that attribute's. */
  boolean countsForAttribute(Candidate candidate, String attribute) {
    return matchesCategory(candidate)
        && matchesAttributesBut(candidate, attribute)
        && matchesPrice(candidate)
        && matchesStock(candidate);
  }

  /** Whether it counts toward the price facet: every filter but the price. */
  boolean countsForPrices(Candidate candidate) {
    return matchesCategory(candidate)
        && matchesAttributesBut(candidate, null)
        && matchesStock(candidate);
  }

  /** Whether it counts toward the in-stock facet: every filter but in-stock. */
  boolean countsForInStock(Candidate candidate) {
    return matchesCategory(candidate)
        && matchesAttributesBut(candidate, null)
        && matchesPrice(candidate);
  }

  private boolean matchesCategory(Candidate candidate) {
    return category == null || category.equals(candidate.product().category());
  }

  /**
   * Whether it has a chosen value, or a value in range, for every attribute filtered on, leaving
   * out {@code skipped}'s filter when it names one.
   */
  private boolean matchesAttributesBut(Candidate candidate, String skipped) {
    var product = candidate.product();
    Predicate<String> filtered = name -> !name.equals(skipped);
    return values.entrySet().stream()
            .filter(f -> filtered.test(f.getKey()))
            .allMatch(f -> product.valuesOf(f.getKey()).stream().anyMatch(f.getValue()::contains))
        && ranges.entrySet().stream()
            .filter(f -> filtered.test(f.getKey()))
            .allMatch(f -> product.valuesOf(f.getKey()).stream().anyMatch(f.getValue()::contains));
  }

  private boolean matchesPrice(Candidate candidate) {
    return price == null || price.contains(candidate.product());
  }

  private boolean matchesStock(Candidate candidate) {
    return !inStockOnly || candidate.inStock();
  }
}
