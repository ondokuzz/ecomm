package com.ecomm.supportassistant.adapter.out.http;

import com.ecomm.supportassistant.adapter.out.http.ContextReads.MoneyBody;
import com.ecomm.supportassistant.application.port.out.Lookup;
import com.ecomm.supportassistant.application.port.out.SearchDiscoveryPort;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Search & Discovery's reads, {@code GET /search?facets=false} and {@code GET /products/{sku}},
 * from its projection of Catalog's Products and Inventory's Stock.
 */
@Component
class SearchDiscoveryClient implements SearchDiscoveryPort {

  private static final String CONTEXT = "Search & Discovery";

  private final RestClient http;

  SearchDiscoveryClient(@Qualifier("searchDiscoveryRestClient") RestClient http) {
    this.http = http;
  }

  private record SummaryBody(
      String sku,
      String name,
      MoneyBody priceFrom,
      boolean priceVaries,
      boolean inStock,
      Map<String, Object> attributes) {

    ProductSummary toSummary() {
      return new ProductSummary(
          sku, name, priceFrom.toMoney(), priceVaries, inStock, orNone(attributes));
    }
  }

  private record PageBody(List<SummaryBody> items) {}

  private record CategoryBody(String slug, String name) {}

  private record WarrantyBody(int months, String manufacturer) {}

  private record VariantBody(
      String id, Map<String, String> axisValues, MoneyBody price, boolean inStock) {

    VariantView toView() {
      return new VariantView(id, orNone(axisValues), price.toMoney(), inStock);
    }
  }

  private record ProductBody(
      String sku,
      String name,
      CategoryBody category,
      Map<String, Object> attributes,
      WarrantyBody warranty,
      List<VariantBody> variants) {

    ProductView toView() {
      return new ProductView(
          sku,
          name,
          category.name(),
          orNone(attributes),
          warranty == null ? null : new WarrantyTerms(warranty.months(), warranty.manufacturer()),
          variants.stream().map(VariantBody::toView).toList());
    }
  }

  /** A map the answer left out, as an empty one. */
  private static <V> Map<String, V> orNone(Map<String, V> values) {
    return values == null ? Map.of() : values;
  }

  @Override
  public Lookup<List<ProductSummary>> search(String query, int limit) {
    return ContextReads.lookup(
        CONTEXT,
        http.get()
            .uri(
                uri ->
                    uri.path("/search")
                        .queryParam("q", query)
                        .queryParam("facets", false)
                        .queryParam("page", 0)
                        .queryParam("size", limit)
                        .build()),
        new ParameterizedTypeReference<PageBody>() {},
        page -> page.items().stream().map(SummaryBody::toSummary).toList());
  }

  @Override
  public Lookup<ProductView> product(String sku) {
    return ContextReads.lookup(
        CONTEXT,
        http.get().uri("/products/{sku}", sku),
        new ParameterizedTypeReference<ProductBody>() {},
        ProductBody::toView);
  }
}
