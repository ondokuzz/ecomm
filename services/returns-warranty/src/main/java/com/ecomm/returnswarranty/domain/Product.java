package com.ecomm.returnswarranty.domain;

import java.util.Optional;
import java.util.Set;

/**
 * What Returns & Warranty keeps of a Catalog Product: its Warranty terms and Serial tracking, for
 * every Variant it has ever had.
 *
 * @param version the newest version of Catalog's events applied
 * @param warranty null when it has no Warranty terms
 */
public record Product(
    String sku,
    long version,
    SerialTracking serialTracking,
    WarrantyTerms warranty,
    Set<String> variantIds) {

  public Product {
    variantIds = Set.copyOf(variantIds);
  }

  public Optional<WarrantyTerms> warrantyTerms() {
    return Optional.ofNullable(warranty);
  }
}
