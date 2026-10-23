package com.ecomm.notifications.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * An {@code RMA_EXPIRED} message's content: the RMA's units not received by its Send-back deadline.
 */
public record RmaExpired(RmaSummary rma, Instant sendBackBy) implements RmaMessage {

  @Override
  public MessageType type() {
    return MessageType.RMA_EXPIRED;
  }

  /** Besides the RMA's, {@code sendBackBy}: a section, empty without one. */
  @Override
  public Map<String, Object> model(Locale locale) {
    var model = rma.model();
    model.put(
        "sendBackBy",
        Formats.section(sendBackBy == null ? null : Formats.date(sendBackBy, locale)));
    return model;
  }
}
