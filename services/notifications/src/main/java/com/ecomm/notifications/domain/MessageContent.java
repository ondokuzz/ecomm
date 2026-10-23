package com.ecomm.notifications.domain;

import java.util.Locale;
import java.util.Map;

/**
 * What a Notification is about, taken from the event that triggered it: everything its message is
 * rendered from but the Recipient.
 */
public sealed interface MessageContent
    permits OrderConfirmation,
        OrderShipped,
        OrderDelivered,
        ReplacementShipped,
        PaymentRefunded,
        RmaMessage {

  MessageType type();

  /** The Customer it is sent to. */
  String customerId();

  /** The Order it is about, or {@code null}. */
  String orderId();

  /** The RMA it is about, or {@code null}. */
  default String rmaId() {
    return null;
  }

  /** The model its templates are rendered against, with Money and dates written for the locale. */
  Map<String, Object> model(Locale locale);
}
