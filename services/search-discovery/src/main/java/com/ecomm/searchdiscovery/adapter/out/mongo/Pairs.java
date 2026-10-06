package com.ecomm.searchdiscovery.adapter.out.mongo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;

/**
 * Attribute and axis values stored as a list of {@code {"name", "value"}} pairs, as Catalog stores
 * axis values: names are Staff's own words, which may hold a dot or start with {@code $}, and a
 * list keeps their order.
 */
final class Pairs {

  private Pairs() {}

  static List<Document> of(Map<String, String> values) {
    return values.entrySet().stream()
        .map(e -> new Document("name", e.getKey()).append("value", e.getValue()))
        .toList();
  }

  static Map<String, String> from(List<Document> pairs) {
    var values = new LinkedHashMap<String, String>();
    pairs.forEach(p -> values.put(p.getString("name"), p.getString("value")));
    return values;
  }
}
