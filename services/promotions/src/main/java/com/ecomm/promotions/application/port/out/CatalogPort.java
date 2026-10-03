package com.ecomm.promotions.application.port.out;

import com.ecomm.promotions.domain.CatalogUnavailableException;
import java.util.Set;

/** Catalog's public reads, which a Campaign's Categories and currencies are checked against. */
public interface CatalogPort {

  /**
   * The slug of every Category Catalog has.
   *
   * @throws CatalogUnavailableException when Catalog can't be reached or fails
   */
  Set<String> categories();

  /**
   * The ISO 4217 code of every currency Catalog prices in.
   *
   * @throws CatalogUnavailableException when Catalog can't be reached or fails
   */
  Set<String> currencies();
}
