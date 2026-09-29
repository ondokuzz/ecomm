package com.ecomm.catalog.domain;

public class CategoryInUseException extends RuntimeException {

  public CategoryInUseException(String slug) {
    super("Category " + slug + " still has Products; move or delete them first");
  }
}
