package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.domain.FieldViolation;
import com.ecomm.catalog.domain.InvalidProductException;
import com.ecomm.catalog.domain.PriceCurrencies;
import com.ecomm.catalog.domain.Product;
import com.ecomm.catalog.domain.SerialTracking;
import com.ecomm.catalog.domain.Variant;
import com.ecomm.catalog.domain.WarrantyTerms;
import com.ecomm.commons.money.Money;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * A Product as Staff send it. On update the SKU comes from the path and may be left out here. A
 * Product sent without {@code warranty} has no Warranty terms, and one without {@code
 * serialTracking} has {@code NONE}.
 */
record ProductRequest(
    String sku,
    String name,
    String description,
    String category,
    Map<String, String> attributes,
    List<String> images,
    List<VariantRequest> variants,
    WarrantyRequest warranty,
    String serialTracking) {

  record WarrantyRequest(Integer months, String manufacturer) {}

  record VariantRequest(
      String id, Map<String, String> axisValues, PriceRequest price, List<String> images) {

    /**
     * This Variant, at {@code index} in the Product's, which names its fields when they're invalid.
     */
    Variant toVariant(int index) {
      var field = "variants[" + index + "].price";
      if (price == null) {
        throw InvalidProductException.of(new FieldViolation(field, "is required"));
      }
      return new Variant(id, axisValues, price.toMoney(field), images);
    }
  }

  record PriceRequest(Long amountMinor, String currency) {

    Money toMoney(String field) {
      if (amountMinor == null || currency == null) {
        throw InvalidProductException.of(
            new FieldViolation(field, "needs an amountMinor and a currency"));
      }
      var priceCurrency =
          PriceCurrencies.of(currency)
              .orElseThrow(
                  () ->
                      InvalidProductException.of(
                          new FieldViolation(
                              field + ".currency",
                              "must be an ISO 4217 currency with a minor unit, e.g. 'EUR'")));
      return new Money(amountMinor, priceCurrency);
    }
  }

  Product toProduct() {
    return toProduct(sku);
  }

  Product toProductWithSku(String pathSku) {
    if (sku != null && !sku.equals(pathSku)) {
      throw new InvalidProductException("sku in the body must match the one in the path");
    }
    return toProduct(pathSku);
  }

  private Product toProduct(String sku) {
    var violations = new ArrayList<FieldViolation>();
    var warrantyTerms = warrantyTerms(violations);
    var tracking = serialTracking(violations);
    if (!violations.isEmpty()) {
      throw InvalidProductException.of(violations);
    }
    return new Product(
        sku,
        name,
        description,
        category,
        attributes,
        images,
        variants == null
            ? null
            : IntStream.range(0, variants.size())
                .mapToObj(i -> variants.get(i) == null ? null : variants.get(i).toVariant(i))
                .toList(),
        warrantyTerms,
        tracking);
  }

  /** The Warranty terms sent, or null for none; adds what is wrong with them to {@code found}. */
  private WarrantyTerms warrantyTerms(List<FieldViolation> found) {
    if (warranty == null) {
      return null;
    }
    if (warranty.months() == null) {
      found.add(
          new FieldViolation(
              "warranty.months",
              "is required, from " + WarrantyTerms.MIN_MONTHS + " to " + WarrantyTerms.MAX_MONTHS));
    }
    try {
      var months = warranty.months() == null ? WarrantyTerms.MIN_MONTHS : warranty.months();
      return new WarrantyTerms(months, warranty.manufacturer());
    } catch (InvalidProductException e) {
      found.addAll(e.violations());
      return null;
    }
  }

  /** The Serial tracking sent, {@code NONE} when left out; adds a value it can't read to found. */
  private SerialTracking serialTracking(List<FieldViolation> found) {
    if (serialTracking == null) {
      return SerialTracking.NONE;
    }
    try {
      return SerialTracking.valueOf(serialTracking);
    } catch (IllegalArgumentException e) {
      found.add(
          new FieldViolation(
              "serialTracking", "must be one of " + Arrays.toString(SerialTracking.values())));
      return null;
    }
  }
}
