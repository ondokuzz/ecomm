package com.ecomm.catalog.adapter.in.web;

import com.ecomm.catalog.domain.InvalidProductException;
import com.ecomm.catalog.domain.Product;
import com.ecomm.commons.money.Money;
import java.util.List;
import java.util.Map;

/** A Product as Staff send it. On update the SKU comes from the path and may be left out here. */
record ProductRequest(
    String sku,
    String name,
    String category,
    Map<String, String> attributes,
    PriceRequest price,
    List<String> images) {

  record PriceRequest(Long amountMinor, String currency) {

    Money toMoney() {
      if (amountMinor == null || currency == null) {
        throw new InvalidProductException("price needs an amountMinor and a currency");
      }
      try {
        return Money.of(amountMinor, currency);
      } catch (IllegalArgumentException e) {
        throw new InvalidProductException("price currency must be an ISO 4217 code, e.g. 'EUR'");
      }
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
    return new Product(
        sku, name, category, attributes, price == null ? null : price.toMoney(), images);
  }
}
