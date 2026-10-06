package com.ecomm.searchdiscovery.domain;

import java.util.List;

/** One attribute a Category's Products carry; {@code values} are an ENUM's, in their order. */
public record AttributeDefinition(String name, AttributeType type, List<String> values) {

  public AttributeDefinition {
    values = List.copyOf(values);
  }
}
