package com.ecomm.searchdiscovery.domain;

import com.ecomm.commons.money.Money;
import java.util.List;
import java.util.Map;

/** One Variant of a searchable Product, as Catalog last published it. */
public record SearchableVariant(
    String variantId, Map<String, String> axisValues, Money price, List<String> images) {

  public SearchableVariant {
    axisValues = Map.copyOf(axisValues);
    images = List.copyOf(images);
  }
}
