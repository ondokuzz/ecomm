package com.ecomm.notifications.application;

import com.ecomm.notifications.application.port.out.NotificationPreferencesStore;
import com.ecomm.notifications.application.port.out.NotificationStore;
import com.ecomm.notifications.application.port.out.TimeSource;
import com.ecomm.notifications.domain.MessageContent;
import com.ecomm.notifications.domain.Notification;
import java.util.ArrayList;
import java.util.List;

/**
 * Records an event's message {@code PENDING} on each channel its Customer's Notification
 * preferences allow, in the caller's transaction. Each claims the event, Message type and channel,
 * so a redelivered event records nothing more.
 */
class Ledger {

  private final NotificationStore notifications;
  private final NotificationPreferencesStore preferences;
  private final TimeSource time;

  Ledger(
      NotificationStore notifications, NotificationPreferencesStore preferences, TimeSource time) {
    this.notifications = notifications;
    this.preferences = preferences;
    this.time = time;
  }

  /** What was recorded, such as {@code ORDER_SHIPPED EMAIL}; empty for a repeat. */
  List<String> record(String eventId, MessageContent content) {
    var channels = preferences.preferencesOf(content.customerId()).channelsFor(content.type());
    var recorded = new ArrayList<String>();
    var now = time.now();
    for (var channel : channels) {
      var notification = Notification.record(eventId, channel, content, now);
      if (notifications.recordIfNew(notification)) {
        recorded.add(notification.type() + " " + notification.channel());
      }
    }
    return recorded;
  }
}
