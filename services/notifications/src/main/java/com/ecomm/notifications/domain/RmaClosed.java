package com.ecomm.notifications.domain;

import java.util.Locale;
import java.util.Map;

/**
 * An {@code RMA_CLOSED} message's content: the RMA closed, with no Refund or replacement, after an
 * Inspection that refused it, and the Inspection's note.
 */
public record RmaClosed(RmaSummary rma, String note) implements RmaMessage {

  @Override
  public MessageType type() {
    return MessageType.RMA_CLOSED;
  }

  /** Besides the RMA's, {@code note}: a section, empty without one. */
  @Override
  public Map<String, Object> model(Locale locale) {
    var model = rma.model();
    model.put("note", Formats.section(note));
    return model;
  }
}
