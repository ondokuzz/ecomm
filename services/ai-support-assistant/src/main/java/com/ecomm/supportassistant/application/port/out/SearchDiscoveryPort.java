package com.ecomm.supportassistant.application.port.out;

import com.ecomm.commons.money.Money;
import java.util.List;
import java.util.Map;

/**
 * Search & Discovery's reads of the Catalog's Products, with their Prices as the Catalog holds them
 * and whether each Variant is in Stock. Anyone may make them; they carry the Customer's token as
 * every lookup does (ADR 0002).
 */
public interface SearchDiscoveryPort {

  /**
   * The Products matching {@code query}, best match first, at most {@code limit}, without Facets.
   */
  Lookup<List<ProductSummary>> search(String query, int limit);

  /** One Product with its Variants; not found for a SKU the projection doesn't hold. */
  Lookup<ProductView> product(String sku);

  /**
   * A Product as a search answers it.
   *
   * @param priceFrom its lowest Variant's Price
   * @param priceVaries whether its Variants' Prices differ
   * @param inStock whether any Variant is in Stock
   */
  record ProductSummary(
      String sku,
      String name,
      Money priceFrom,
      boolean priceVaries,
      boolean inStock,
      Map<String, Object> attributes) {}

  /**
   * One Product.
   *
   * @param category its Category's name
   * @param warranty null when it has no Warranty terms
   */
  record ProductView(
      String sku,
      String name,
      String category,
      Map<String, Object> attributes,
      WarrantyTerms warranty,
      List<VariantView> variants) {}

  record WarrantyTerms(int months, String manufacturer) {}

  /**
   * @param axisValues its value on each Variant axis, such as {@code color} and {@code storage}
   */
  record VariantView(String id, Map<String, String> axisValues, Money price, boolean inStock) {}
}
