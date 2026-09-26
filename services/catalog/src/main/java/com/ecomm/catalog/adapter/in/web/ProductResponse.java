package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.domain.Product;
import java.util.List;
import java.util.Map;

/**
 * A Product as clients see it. {@code variants} always lists the purchasable Variants, even while
 * each Product has just one, so clients add Variant IDs (not SKUs) to a Cart from day one.
 */
record ProductResponse(
    String sku,
    String name,
    String category,
    Map<String, String> attributes,
    PriceResponse price,
    List<String> images,
    List<VariantResponse> variants) {

  record VariantResponse(String id, PriceResponse price) {}

  static ProductResponse of(Product product) {
    return new ProductResponse(
        product.sku(),
        product.name(),
        product.category(),
        product.attributes(),
        PriceResponse.of(product.price()),
        product.images(),
        product.variants().stream()
            .map(v -> new VariantResponse(v.id(), PriceResponse.of(v.price())))
            .toList());
  }
}
