package com.ecomm.promotions.domain;

/** Catalog couldn't say which Categories or currencies it has, so a Campaign wasn't saved. */
public class CatalogUnavailableException extends RuntimeException {

  public CatalogUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
