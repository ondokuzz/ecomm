package com.ecomm.searchdiscovery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecomm.commons.money.Money;
import com.ecomm.searchdiscovery.domain.Facets.AttributeFacet;
import com.ecomm.searchdiscovery.domain.Facets.CategoryCount;
import com.ecomm.searchdiscovery.domain.Facets.PriceFacet;
import com.ecomm.searchdiscovery.domain.Facets.ValueCount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Facets are counted in Products, each over the candidates matching every filter but the facet's
 * own, so a chosen value leaves its siblings' counts showing what choosing them too would add.
 */
class FacetCountingTest {

  private static final SearchCategory PHONES =
      new SearchCategory(
          "phones",
          1,
          "Phones",
          List.of(
              new AttributeDefinition("brand", AttributeType.TEXT, List.of()),
              new AttributeDefinition(
                  "storage", AttributeType.ENUM, List.of("128 GB", "256 GB", "512 GB", "1 TB")),
              new AttributeDefinition("screen", AttributeType.NUMBER, List.of())),
          false);
  private static final SearchCategory LAPTOPS =
      new SearchCategory("laptops", 1, "Laptops", List.of(), false);

  // Google: 128 + 256 GB, 6.3 in, EUR 699–799, in Stock
  // Google: 512 GB, 6.8 in, EUR 999, out of Stock
  // Apple: 128 GB, 6.1 in, EUR 899, in Stock
  // A laptop, EUR 1499, in Stock
  // A Product in a Category whose event hasn't arrived, USD 50
  private final List<Candidate> candidates =
      List.of(
          phone("P1", "Google", "6.3", true, variant("128 GB", 69900), variant("256 GB", 79900)),
          phone("P2", "Google", "6.8", false, variant("512 GB", 99900)),
          phone("P3", "Apple", "6.1", true, variant("128 GB", 89900)),
          candidate("L1", "laptops", Map.of(), true, variant(Map.of(), 149900, "EUR")),
          candidate("X1", "gadgets", Map.of(), false, variant(Map.of(), 5000, "USD")));

  @Test
  void categoriesAreCountedWithoutTheCategoryFilter() {
    var facets = facets(request().category("phones").value("brand", "Google"));

    assertThat(facets.categories())
        .containsExactly(
            new CategoryCount("gadgets", "gadgets", 0),
            new CategoryCount("laptops", "Laptops", 0),
            new CategoryCount("phones", "Phones", 2));
    assertThat(facets(request()).categories())
        .extracting(CategoryCount::count)
        .containsExactly(1L, 1L, 3L);
  }

  @Test
  void attributeFacetsComeOnlyOnceACategoryIsChosenInItsDefinitionsOrder() {
    assertThat(facets(request()).attributes()).isEmpty();

    assertThat(facets(request().category("phones")).attributes())
        .extracting(AttributeFacet::name)
        .containsExactly("brand", "storage", "screen");
  }

  @Test
  void aChosenValueLeavesItsSiblingsCounted() {
    var facets = facets(request().category("phones").value("storage", "128 GB"));

    assertThat(facet(facets, "storage").values())
        .containsExactly(
            new ValueCount("128 GB", 2),
            new ValueCount("256 GB", 1),
            new ValueCount("512 GB", 1),
            new ValueCount("1 TB", 0));
  }

  @Test
  void otherAttributesAreCountedWithTheChosenValue() {
    var facets = facets(request().category("phones").value("storage", "128 GB"));

    assertThat(facet(facets, "brand").values())
        .containsExactly(new ValueCount("Apple", 1), new ValueCount("Google", 1));
  }

  @Test
  void aProductCountsOnceForAValueSeveralOfItsVariantsHave() {
    var twoBlack =
        candidate(
            "P9",
            "phones",
            Map.of("brand", "Google"),
            true,
            variant(Map.of("storage", "128 GB"), 100, "EUR"),
            variant(Map.of("storage", "128 GB", "color", "Black"), 200, "EUR"));

    var facets =
        Search.facets(request().category("phones").build(), List.of(twoBlack), List.of(PHONES));

    assertThat(facet(facets, "storage").values()).contains(new ValueCount("128 GB", 1));
  }

  @Test
  void aChosenValueNoProductHasIsStillListedSoItCanBeCleared() {
    var facets = facets(request().category("phones").value("brand", "Nokia"));

    assertThat(facet(facets, "brand").values())
        .containsExactly(
            new ValueCount("Apple", 1), new ValueCount("Google", 2), new ValueCount("Nokia", 0));
  }

  @Test
  void aNumberFacetIsItsRangeWithoutItsOwnFilter() {
    var facets =
        facets(
            request()
                .category("phones")
                .range("screen", new NumberRange(new BigDecimal("6.2"), null))
                .value("brand", "Google"));

    var screen = facet(facets, "screen");
    assertThat(screen.values()).isEmpty();
    assertThat(screen.min()).isEqualByComparingTo("6.3");
    assertThat(screen.max()).isEqualByComparingTo("6.8");
  }

  @Test
  void pricesAreRangedPerCurrencyWithoutThePriceFilter() {
    var facets = facets(request().price(new PriceRange(Currency.getInstance("EUR"), 0L, 70000L)));

    assertThat(facets.prices())
        .containsExactly(
            new PriceFacet(Currency.getInstance("EUR"), 69900, 149900),
            new PriceFacet(Currency.getInstance("USD"), 5000, 5000));
    assertThat(facets(request().category("phones").inStockOnly()).prices())
        .containsExactly(new PriceFacet(Currency.getInstance("EUR"), 69900, 89900));
  }

  @Test
  void theInStockCountIgnoresTheInStockFilterButNotTheOthers() {
    assertThat(facets(request().inStockOnly()).inStock()).isEqualTo(3);
    assertThat(facets(request().category("phones").value("brand", "Google")).inStock())
        .isEqualTo(1);
  }

  @Test
  void theResultsApplyEveryFilter() {
    var result =
        Search.run(
            request().category("phones").value("storage", "128 GB").inStockOnly().build(),
            candidates,
            List.of(PHONES, LAPTOPS));

    assertThat(result.items()).extracting(c -> c.product().sku()).containsExactly("P1", "P3");
    assertThat(result.total()).isEqualTo(2);
  }

  private Facets facets(RequestBuilder request) {
    return Search.facets(request.build(), candidates, List.of(PHONES, LAPTOPS));
  }

  private static AttributeFacet facet(Facets facets, String name) {
    return facets.attributes().stream()
        .filter(a -> a.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static RequestBuilder request() {
    return new RequestBuilder();
  }

  private static final class RequestBuilder {
    private String category;
    private final Map<String, Set<String>> values = new LinkedHashMap<>();
    private final Map<String, NumberRange> ranges = new LinkedHashMap<>();
    private PriceRange price;
    private boolean inStockOnly;

    RequestBuilder category(String category) {
      this.category = category;
      return this;
    }

    RequestBuilder value(String attribute, String value) {
      values.put(attribute, Set.of(value));
      return this;
    }

    RequestBuilder range(String attribute, NumberRange range) {
      ranges.put(attribute, range);
      return this;
    }

    RequestBuilder price(PriceRange price) {
      this.price = price;
      return this;
    }

    RequestBuilder inStockOnly() {
      this.inStockOnly = true;
      return this;
    }

    SearchRequest build() {
      return new SearchRequest(
          null, category, values, ranges, price, inStockOnly, SortOrder.NEWEST, 0, 24);
    }
  }

  private static Candidate phone(
      String sku, String brand, String screen, boolean inStock, SearchableVariant... variants) {
    return candidate(sku, "phones", Map.of("brand", brand, "screen", screen), inStock, variants);
  }

  private static SearchableVariant variant(String storage, long priceMinor) {
    return variant(Map.of("storage", storage), priceMinor, "EUR");
  }

  private static int variants;

  private static SearchableVariant variant(
      Map<String, String> axisValues, long priceMinor, String currency) {
    return new SearchableVariant(
        "V" + ++variants, axisValues, Money.of(priceMinor, currency), List.of());
  }

  private static Candidate candidate(
      String sku,
      String category,
      Map<String, String> attributes,
      boolean inStock,
      SearchableVariant... variants) {
    return new Candidate(
        new SearchableProduct(
            sku,
            1,
            sku,
            null,
            category,
            attributes,
            List.of(),
            new ArrayList<>(List.of(variants)),
            false,
            Instant.parse("2026-10-01T00:00:00Z")),
        inStock,
        0);
  }
}
