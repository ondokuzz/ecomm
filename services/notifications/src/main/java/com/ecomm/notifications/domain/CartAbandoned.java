package com.ecomm.notifications.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A {@code CART_ABANDONED} message's content, a Cart reminder: the Customer's Abandoned Cart as
 * Cart answered it when the reminder was recorded, each line with its Price and Stock then, and the
 * Storefront's link to the Cart, and the link that turns cart reminders off without signing in, its
 * token issued as the reminder was recorded. Only the lines still sold are in it, and at least one
 * is.
 */
public record CartAbandoned(
    String customerId, List<RemindedLine> lines, String cartUrl, String unsubscribeUrl)
    implements MessageContent {

  public CartAbandoned {
    lines = List.copyOf(lines);
  }

  @Override
  public MessageType type() {
    return MessageType.CART_ABANDONED;
  }

  /** A Cart is about no Order. */
  @Override
  public String orderId() {
    return null;
  }

  @Override
  public Map<String, Object> model(Locale locale) {
    var model = new LinkedHashMap<String, Object>();
    model.put("lines", RemindedLine.model(lines, locale));
    model.put("cartUrl", cartUrl);
    model.put("unsubscribeUrl", unsubscribeUrl);
    return model;
  }
}
