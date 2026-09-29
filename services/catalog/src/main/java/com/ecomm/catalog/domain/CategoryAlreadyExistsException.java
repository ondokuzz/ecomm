package com.ecomm.catalog.domain;

public class CategoryAlreadyExistsException extends RuntimeException {

  public CategoryAlreadyExistsException(String slug) {
    super("A Category with slug " + slug + " already exists");
  }
}
