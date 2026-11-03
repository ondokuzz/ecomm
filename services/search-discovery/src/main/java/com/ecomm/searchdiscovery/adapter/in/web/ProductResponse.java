package com.ecomm.searchdiscovery.adapter.in.web;

import com.ecomm.searchdiscovery.adapter.in.web.SearchResponse.MoneyResponse;
import com.ecomm.searchdiscovery.domain.ListedProduct;
import com.ecomm.searchdiscovery.domain.WarrantyTerms;
import java.util.List;
import java.util.Map;

/**
 * One Product: its name, its Category's slug and name, its attributes and Warranty terms, null when
 * it has none, and each Variant with its axis values, Price and whether it is in Stock, but not how
 * many.
 */
record ProductResponse(
    String sku,
    String name,
    CategoryResponse category,
    Map<String, String> attributes,
    WarrantyTerms warranty,
    List<VariantResponse> variants) {

  record CategoryResponse(String slug, String name) {}

  record VariantResponse(
      String id, Map<String, String> axisValues, MoneyResponse price, boolean inStock) {}

  static ProductResponse of(ListedProduct listed) {
    var product = listed.product();
    return new ProductResponse(
        product.sku(),
        product.name(),
        new CategoryResponse(product.category(), listed.categoryLabel()),
        product.attributes(),
        product.warranty(),
        product.variants().stream()
            .map(
                v ->
                    new VariantResponse(
                        v.variantId(),
                        v.axisValues(),
                        MoneyResponse.of(v.price()),
                        listed.inStock(v)))
            .toList());
  }
}
