package com.ecomm.searchdiscovery.domain;

import com.ecomm.searchdiscovery.domain.Facets.AttributeFacet;
import com.ecomm.searchdiscovery.domain.Facets.CategoryCount;
import com.ecomm.searchdiscovery.domain.Facets.PriceFacet;
import com.ecomm.searchdiscovery.domain.Facets.ValueCount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs a search over the candidates its text matched: filters them, orders and pages the matches,
 * and counts the facets. Every facet is counted over the candidates that match every filter but its
 * own (disjunctive counting).
 */
public final class Search {

  private static final Comparator<Candidate> NEWEST =
      Comparator.comparing((Candidate c) -> c.product().listedAt())
          .reversed()
          .thenComparing(c -> c.product().sku());
  // Amounts in different Currencies don't compare, so a price sort groups Products by Currency.
  private static final Comparator<Candidate> CURRENCY =
      Comparator.comparing(c -> c.product().priceFrom().currency().getCurrencyCode());
  private static final Comparator<Candidate> AMOUNT =
      Comparator.comparingLong(c -> c.product().priceFrom().amountMinor());
  private static final Comparator<Candidate> SKU = Comparator.comparing(c -> c.product().sku());

  private Search() {}

  /**
   * @param candidates the Products the request's text matched, or every Product when it has none;
   *     none of them removed
   * @param categories every Category, none of them removed
   */
  public static SearchResult run(
      SearchRequest request, List<Candidate> candidates, List<SearchCategory> categories) {
    var matches = candidates.stream().filter(request::matches).sorted(orderOf(request)).toList();
    var page =
        matches.stream()
            .skip((long) request.page() * request.size())
            .limit(request.size())
            .toList();
    return new SearchResult(page, matches.size(), facets(request, candidates, categories));
  }

  private static Comparator<Candidate> orderOf(SearchRequest request) {
    return switch (request.sort()) {
      case RELEVANCE ->
          request.text() == null
              ? NEWEST
              : Comparator.comparingDouble(Candidate::relevance).reversed().thenComparing(NEWEST);
      case NEWEST -> NEWEST;
      case PRICE_ASC -> CURRENCY.thenComparing(AMOUNT).thenComparing(SKU);
      case PRICE_DESC -> CURRENCY.thenComparing(AMOUNT.reversed()).thenComparing(SKU);
    };
  }

  static Facets facets(
      SearchRequest request, List<Candidate> candidates, List<SearchCategory> categories) {
    return new Facets(
        categoryCounts(request, candidates, categories),
        attributeFacets(request, candidates, categories),
        priceFacets(request, candidates),
        candidates.stream().filter(request::countsForInStock).filter(Candidate::inStock).count());
  }

  /** Every Category by name, with the Products in it that count toward it. */
  private static List<CategoryCount> categoryCounts(
      SearchRequest request, List<Candidate> candidates, List<SearchCategory> categories) {
    var counts =
        candidates.stream()
            .filter(request::countsForCategories)
            .collect(Collectors.groupingBy(c -> c.product().category(), Collectors.counting()));
    var names = new TreeMap<String, String>();
    categories.forEach(category -> names.put(category.slug(), category.name()));
    // A Product may name a Category whose event hasn't arrived yet; it is listed by its slug.
    candidates.forEach(c -> names.putIfAbsent(c.product().category(), c.product().category()));
    return names.entrySet().stream()
        .map(e -> new CategoryCount(e.getKey(), e.getValue(), counts.getOrDefault(e.getKey(), 0L)))
        .sorted(
            Comparator.comparing(CategoryCount::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(CategoryCount::slug))
        .toList();
  }

  /** Once a Category is chosen, a facet per Attribute definition it has, in its order. */
  private static List<AttributeFacet> attributeFacets(
      SearchRequest request, List<Candidate> candidates, List<SearchCategory> categories) {
    var chosen = categories.stream().filter(c -> c.slug().equals(request.category())).findFirst();
    if (chosen.isEmpty()) {
      return List.of();
    }
    return chosen.get().attributes().stream()
        .map(
            definition -> {
              var products =
                  candidates.stream()
                      .filter(c -> request.countsForAttribute(c, definition.name()))
                      .map(Candidate::product)
                      .toList();
              return definition.type() == AttributeType.NUMBER
                  ? numberFacet(definition, products)
                  : valueFacet(definition, products, request);
            })
        .toList();
  }

  private static AttributeFacet numberFacet(
      AttributeDefinition definition, List<SearchableProduct> products) {
    var numbers =
        products.stream()
            .flatMap(p -> p.valuesOf(definition.name()).stream())
            .map(NumberRange::decimal)
            .flatMap(Optional::stream)
            .toList();
    return new AttributeFacet(
        definition.name(),
        definition.type(),
        List.of(),
        numbers.stream().min(Comparator.naturalOrder()).orElse(null),
        numbers.stream().max(Comparator.naturalOrder()).orElse(null));
  }

  /**
   * An ENUM's values in its definition's order, then any others the Products have; another type's
   * in alphabetical order. A chosen value is listed even when no Product has it, so it can be
   * cleared.
   */
  private static AttributeFacet valueFacet(
      AttributeDefinition definition, List<SearchableProduct> products, SearchRequest request) {
    var counts =
        products.stream()
            .flatMap(p -> p.valuesOf(definition.name()).stream())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    var others = new TreeSet<>(counts.keySet());
    others.addAll(request.values().getOrDefault(definition.name(), Set.of()));
    var values = new LinkedHashSet<String>();
    if (definition.type() == AttributeType.ENUM) {
      values.addAll(definition.values());
    }
    values.addAll(others);
    return new AttributeFacet(
        definition.name(),
        definition.type(),
        values.stream().map(v -> new ValueCount(v, counts.getOrDefault(v, 0L))).toList(),
        null,
        null);
  }

  /** The range of Variant Prices in each Currency, over the Products matching all but the price. */
  private static List<PriceFacet> priceFacets(SearchRequest request, List<Candidate> candidates) {
    var ranges = new TreeMap<String, PriceFacet>();
    candidates.stream()
        .filter(request::countsForPrices)
        .flatMap(c -> c.product().variants().stream())
        .map(SearchableVariant::price)
        .forEach(
            price ->
                ranges.merge(
                    price.currency().getCurrencyCode(),
                    new PriceFacet(price.currency(), price.amountMinor(), price.amountMinor()),
                    (a, b) -> a.widenedBy(price)));
    return new ArrayList<>(ranges.values());
  }
}
