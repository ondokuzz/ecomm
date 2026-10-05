package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.domain.Product;
import com.ecomm.catalog.domain.Variant;
import java.util.List;
import java.util.Map;

/**
 * A Product as clients see it: its purchasable Variants, and {@code priceFrom}, the lowest of their
 * Prices. {@code description} is null when it has none. Clients add Variant IDs, not SKUs, to a
 * Cart.
 */
record ProductResponse(
    String sku,
    String name,
    String description,
    String category,
    Map<String, String> attributes,
    PriceResponse priceFrom,
    List<String> images,
    List<VariantResponse> variants) {

  /** A Variant; {@code images} is empty unless it has its own, which replace its Product's. */
  record VariantResponse(
      String id, Map<String, String> axisValues, PriceResponse price, List<String> images) {

    static VariantResponse of(Variant variant) {
      return new VariantResponse(
          variant.id(), variant.axisValues(), PriceResponse.of(variant.price()), variant.images());
    }
  }

  static ProductResponse of(Product product) {
    return new ProductResponse(
        product.sku(),
        product.name(),
        product.description(),
        product.category(),
        product.attributes(),
        PriceResponse.of(product.priceFrom()),
        product.images(),
        product.variants().stream().map(VariantResponse::of).toList());
  }
}
