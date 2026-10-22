package com.ecomm.notifications.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An {@code ORDER_SHIPPED} message's content: an Order's Shipment handed to its Carrier, with what
 * it holds and how to follow it. {@code carrier} and {@code trackingNumber} are {@code null} for a
 * Shipment its event didn't say was booked.
 */
public record OrderShipped(
    String orderId,
    String customerId,
    String carrier,
    String trackingNumber,
    List<ShippedLine> lines)
    implements MessageContent {

  public OrderShipped {
    lines = List.copyOf(lines);
  }

  @Override
  public MessageType type() {
    return MessageType.ORDER_SHIPPED;
  }

  @Override
  public Map<String, Object> model(Locale locale) {
    var model = new LinkedHashMap<String, Object>();
    model.put("order", Map.of("reference", Formats.orderReference(orderId)));
    model.put("lines", ShippedLine.model(lines));
    model.put("summary", ShippedLine.summary(lines));
    model.put("tracking", ShippedLine.tracking(carrier, trackingNumber));
    return model;
  }
}
