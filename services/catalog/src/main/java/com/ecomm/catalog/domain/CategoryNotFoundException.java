package com.ecomm.catalog.domain;

public class CategoryNotFoundException extends RuntimeException {

  public CategoryNotFoundException(String slug) {
    super("No Category with slug " + slug);
  }
}
