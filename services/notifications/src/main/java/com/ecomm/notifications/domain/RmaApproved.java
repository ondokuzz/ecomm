package com.ecomm.notifications.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * An {@code RMA_APPROVED} message's content: the RMA approved, the Return address of the Warehouse
 * its units go back to, and its Send-back deadline. {@code returnAddress} is {@code null} when its
 * Warehouse wasn't known, and {@code sendBackBy} for an RMA approved without one.
 */
public record RmaApproved(RmaSummary rma, ReturnAddress returnAddress, Instant sendBackBy)
    implements RmaMessage {

  @Override
  public MessageType type() {
    return MessageType.RMA_APPROVED;
  }

  /** Besides the RMA's, {@code returnAddress} and {@code sendBackBy}, each a section. */
  @Override
  public Map<String, Object> model(Locale locale) {
    var model = rma.model();
    model.put(
        "returnAddress",
        Formats.section(returnAddress == null ? null : returnAddress.model(locale)));
    model.put(
        "sendBackBy",
        Formats.section(sendBackBy == null ? null : Formats.date(sendBackBy, locale)));
    return model;
  }
}
