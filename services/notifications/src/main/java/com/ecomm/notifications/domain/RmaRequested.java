package com.ecomm.notifications.domain;

import java.util.Locale;
import java.util.Map;

/** An {@code RMA_REQUESTED} message's content: the Customer's RMA, recorded for Staff to decide. */
public record RmaRequested(RmaSummary rma) implements RmaMessage {

  @Override
  public MessageType type() {
    return MessageType.RMA_REQUESTED;
  }

  @Override
  public Map<String, Object> model(Locale locale) {
    return rma.model();
  }
}
