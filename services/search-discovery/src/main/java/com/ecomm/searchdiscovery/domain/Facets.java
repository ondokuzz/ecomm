package com.ecomm.searchdiscovery.domain;

import com.ecomm.commons.money.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

/**
 * What a search's results could be narrowed by, and how many Products each choice would leave. Each
 * facet is counted as if its own filter weren't applied (disjunctive counting), so choosing one
 * value still shows its siblings' counts.
 *
 * @param categories every Category, with how many Products each holds
 * @param attributes once a Category is chosen, each of its Attribute definitions, in its order
 * @param prices the range of Variant Prices in each Currency
 * @param inStock how many Products have a Variant in Stock
 */
public record Facets(
    List<CategoryCount> categories,
    List<AttributeFacet> attributes,
    List<PriceFacet> prices,
    long inStock) {

  public record CategoryCount(String slug, String name, long count) {}

  /**
   * One Attribute definition's facet: for ENUM, TEXT and BOOLEAN its values with their counts; for
   * NUMBER the lowest and highest value, null when no Product has one.
   */
  public record AttributeFacet(
      String name, AttributeType type, List<ValueCount> values, BigDecimal min, BigDecimal max) {}

  public record ValueCount(String value, long count) {}

  /** The lowest and highest Variant Price in one Currency. */
  public record PriceFacet(Currency currency, long min, long max) {

    PriceFacet widenedBy(Money price) {
      return new PriceFacet(
          currency, Math.min(min, price.amountMinor()), Math.max(max, price.amountMinor()));
    }
  }
}
