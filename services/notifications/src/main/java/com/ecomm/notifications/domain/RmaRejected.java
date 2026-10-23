package com.ecomm.notifications.domain;

import java.util.Locale;
import java.util.Map;

/** An {@code RMA_REJECTED} message's content: the RMA rejected, and Staff's reason. */
public record RmaRejected(RmaSummary rma, String reason) implements RmaMessage {

  @Override
  public MessageType type() {
    return MessageType.RMA_REJECTED;
  }

  /** Besides the RMA's, {@code reason}: a section, empty without one. */
  @Override
  public Map<String, Object> model(Locale locale) {
    var model = rma.model();
    model.put("reason", Formats.section(reason));
    return model;
  }
}
