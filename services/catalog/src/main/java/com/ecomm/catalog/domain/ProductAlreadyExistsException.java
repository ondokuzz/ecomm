package com.ecomm.catalog.domain;

public class ProductAlreadyExistsException extends RuntimeException {

  public ProductAlreadyExistsException(String sku) {
    super("A Product with SKU " + sku + " already exists");
  }
}
