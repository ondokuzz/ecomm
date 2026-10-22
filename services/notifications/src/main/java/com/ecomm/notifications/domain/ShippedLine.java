package com.ecomm.notifications.domain;

import java.util.List;
import java.util.Map;

/** One line of a Shipment, as its messages list it: the Product's name, and how many. */
public record ShippedLine(String name, int quantity) {

  static List<Map<String, Object>> model(List<ShippedLine> lines) {
    return lines.stream()
        .map(l -> Map.<String, Object>of("name", l.name(), "quantity", l.quantity()))
        .toList();
  }

  /** The Carrier and tracking number as a section: empty when the Shipment wasn't booked. */
  static List<Map<String, Object>> tracking(String carrier, String trackingNumber) {
    return carrier == null || trackingNumber == null
        ? List.of()
        : List.of(Map.of("carrier", carrier, "number", trackingNumber));
  }

  /** The lines as one short phrase, such as {@code 2 × Pixel 9 and 1 more}, for SMS and push. */
  static String summary(List<ShippedLine> lines) {
    if (lines.isEmpty()) {
      return "";
    }
    var first = lines.getFirst().quantity() + " × " + lines.getFirst().name();
    return lines.size() == 1 ? first : first + " and " + (lines.size() - 1) + " more";
  }
}
