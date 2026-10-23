package com.ecomm.notifications.domain;

import java.util.Locale;
import java.util.Map;

/** An {@code RMA_RECEIVED} message's content: the RMA's units received at its Warehouse. */
public record RmaReceived(RmaSummary rma) implements RmaMessage {

  @Override
  public MessageType type() {
    return MessageType.RMA_RECEIVED;
  }

  @Override
  public Map<String, Object> model(Locale locale) {
    return rma.model();
  }
}
