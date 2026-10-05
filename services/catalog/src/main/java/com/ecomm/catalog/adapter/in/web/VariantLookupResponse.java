package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.domain.Product;
import com.ecomm.catalog.domain.Variant;
import java.util.List;
import java.util.Map;

/**
 * One Variant, looked up by its ID, with enough of its Product to show it: what a Cart line or a
 * checkout needs, including the Product's Category (its slug), which decides the Campaigns that
 * apply to it. {@code images} is empty unless the Variant has its own.
 */
record VariantLookupResponse(
    String id,
    Map<String, String> axisValues,
    PriceResponse price,
    List<String> images,
    ProductSummary product) {

  record ProductSummary(String sku, String name, String category, List<String> images) {}

  static VariantLookupResponse of(Product product, Variant variant) {
    return new VariantLookupResponse(
        variant.id(),
        variant.axisValues(),
        PriceResponse.of(variant.price()),
        variant.images(),
        new ProductSummary(product.sku(), product.name(), product.category(), product.images()));
  }
}
