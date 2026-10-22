package com.ecomm.notifications.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** An {@code ORDER_DELIVERED} message's content: an Order's Shipment delivered, and when. */
public record OrderDelivered(
    String orderId, String customerId, Instant deliveredAt, List<ShippedLine> lines)
    implements MessageContent {

  public OrderDelivered {
    lines = List.copyOf(lines);
  }

  @Override
  public MessageType type() {
    return MessageType.ORDER_DELIVERED;
  }

  @Override
  public Map<String, Object> model(Locale locale) {
    var model = new LinkedHashMap<String, Object>();
    model.put("order", Map.of("reference", Formats.orderReference(orderId)));
    model.put("deliveredOn", Formats.date(deliveredAt, locale));
    model.put("lines", ShippedLine.model(lines));
    model.put("summary", ShippedLine.summary(lines));
    return model;
  }
}
