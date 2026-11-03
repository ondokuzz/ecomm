package com.ecomm.searchdiscovery.domain;

public class ProductNotFoundException extends RuntimeException {

  public ProductNotFoundException(String sku) {
    super("No Product with SKU " + sku);
  }
}
