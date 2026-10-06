package com.ecomm.searchdiscovery.adapter.in.web;

import com.ecomm.commons.money.Money;
import com.ecomm.searchdiscovery.domain.Candidate;
import com.ecomm.searchdiscovery.domain.Facets;
import com.ecomm.searchdiscovery.domain.SearchRequest;
import com.ecomm.searchdiscovery.domain.SearchResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** A page of Product summaries, how many matched in all, and the facets to narrow them by. */
record SearchResponse(List<Summary> items, int page, int size, long total, FacetsResponse facets) {

  record MoneyResponse(long amountMinor, String currency) {
    static MoneyResponse of(Money money) {
      return new MoneyResponse(money.amountMinor(), money.currency().getCurrencyCode());
    }
  }

  /**
   * What a Product card shows: its first image, null when it has none; its lowest Price, which
   * {@code priceVaries} says to show as "from"; and whether any Variant is in Stock.
   */
  record Summary(
      String sku,
      String name,
      String image,
      MoneyResponse priceFrom,
      boolean priceVaries,
      boolean inStock,
      Map<String, String> attributes) {

    static Summary of(Candidate candidate) {
      var product = candidate.product();
      return new Summary(
          product.sku(),
          product.name(),
          product.image().orElse(null),
          MoneyResponse.of(product.priceFrom()),
          product.priceVaries(),
          candidate.inStock(),
          product.attributes());
    }
  }

  record FacetsResponse(
      List<Facets.CategoryCount> categories,
      List<AttributeResponse> attributes,
      List<PriceResponse> prices,
      long inStock) {}

  record AttributeResponse(
      String name, String type, List<Facets.ValueCount> values, BigDecimal min, BigDecimal max) {}

  record PriceResponse(String currency, long min, long max) {}

  static SearchResponse of(SearchRequest request, SearchResult result) {
    var facets = result.facets();
    return new SearchResponse(
        result.items().stream().map(Summary::of).toList(),
        request.page(),
        request.size(),
        result.total(),
        new FacetsResponse(
            facets.categories(),
            facets.attributes().stream()
                .map(
                    a ->
                        new AttributeResponse(
                            a.name(), a.type().name(), a.values(), a.min(), a.max()))
                .toList(),
            facets.prices().stream()
                .map(p -> new PriceResponse(p.currency().getCurrencyCode(), p.min(), p.max()))
                .toList(),
            facets.inStock()));
  }
}
