package com.ecomm.notifications.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A {@code REPLACEMENT_SHIPPED} message's content: the replacement for an RMA's units handed to its
 * Carrier. {@code carrier} and {@code trackingNumber} are {@code null} for one its event didn't say
 * was booked.
 */
public record ReplacementShipped(
    String orderId,
    String rmaId,
    String customerId,
    String carrier,
    String trackingNumber,
    List<ShippedLine> lines)
    implements MessageContent {

  public ReplacementShipped {
    lines = List.copyOf(lines);
  }

  @Override
  public MessageType type() {
    return MessageType.REPLACEMENT_SHIPPED;
  }

  @Override
  public Map<String, Object> model(Locale locale) {
    var model = new LinkedHashMap<String, Object>();
    model.put("order", Map.of("reference", Formats.orderReference(orderId)));
    model.put("rma", Map.of("reference", Formats.rmaReference(rmaId)));
    model.put("lines", ShippedLine.model(lines));
    model.put("summary", ShippedLine.summary(lines));
    model.put("tracking", ShippedLine.tracking(carrier, trackingNumber));
    return model;
  }
}
