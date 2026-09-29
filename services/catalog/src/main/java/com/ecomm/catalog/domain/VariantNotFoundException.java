package com.ecomm.catalog.domain;

public class VariantNotFoundException extends RuntimeException {

  public VariantNotFoundException(String variantId) {
    super("No Variant with ID " + variantId);
  }
}
