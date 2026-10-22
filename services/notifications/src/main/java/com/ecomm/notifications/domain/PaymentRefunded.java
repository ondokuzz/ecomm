package com.ecomm.notifications.domain;

import com.ecomm.commons.money.Money;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A {@code PAYMENT_REFUNDED} message's content: one approved Refund of an Order's Payment, its
 * amount, and what it was for: an RMA's reference, or {@code null}.
 */
public record PaymentRefunded(
    String orderId, String customerId, Money amount, String reference, Instant refundedAt)
    implements MessageContent {

  @Override
  public MessageType type() {
    return MessageType.PAYMENT_REFUNDED;
  }

  @Override
  public Map<String, Object> model(Locale locale) {
    var model = new LinkedHashMap<String, Object>();
    model.put("order", Map.of("reference", Formats.orderReference(orderId)));
    model.put("amount", Formats.money(amount, locale));
    model.put("refundedOn", Formats.date(refundedAt, locale));
    model.put("for", reference == null ? List.of() : List.of(reference));
    return model;
  }
}
