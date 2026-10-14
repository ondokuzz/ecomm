package com.ecomm.fulfillmentshipping.domain;

import java.util.Set;

/**
 * What Fulfillment & Shipping keeps of a Catalog Product: its name and Serial tracking, for every
 * Variant it has ever had.
 *
 * @param version the newest version of Catalog's events applied
 */
public record Product(
    String sku, long version, String name, SerialTracking serialTracking, Set<String> variantIds) {

  public Product {
    variantIds = Set.copyOf(variantIds);
  }
}
