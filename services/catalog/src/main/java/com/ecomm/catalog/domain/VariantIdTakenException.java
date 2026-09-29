package com.ecomm.catalog.domain;

import java.util.List;

/** Some of a Product's Variant IDs already belong to another Product. */
public class VariantIdTakenException extends RuntimeException {

  public VariantIdTakenException(List<String> variantIds) {
    super(
        variantIds.size() == 1
            ? "Variant ID " + variantIds.getFirst() + " already belongs to another Product"
            : "Variant IDs " + String.join(", ", variantIds) + " already belong to other Products");
  }
}
