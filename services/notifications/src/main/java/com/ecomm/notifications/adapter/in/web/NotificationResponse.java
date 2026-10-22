package com.ecomm.notifications.adapter.in.web;

import com.ecomm.notifications.domain.Notification;
import java.time.Instant;
import java.util.List;

/**
 * A Notification as Staff and its Customer read it: the address masked, the message as last
 * rendered ({@code null} before it was), and every attempt.
 */
record NotificationResponse(
    String id,
    String messageType,
    String channel,
    String customerId,
    String orderId,
    String rmaId,
    String recipient,
    String status,
    Message message,
    List<AttemptResponse> attempts,
    Instant createdAt,
    Instant sentAt,
    Instant nextAttemptAt) {

  record Message(String subject, String html, String text) {}

  record AttemptResponse(int number, Instant at, String outcome, String reason) {}

  static NotificationResponse of(Notification n) {
    var message = n.message();
    return new NotificationResponse(
        n.id().toString(),
        n.type().name(),
        n.channel().name(),
        n.customerId(),
        n.orderId(),
        n.rmaId(),
        n.channel().mask(n.recipient()),
        n.status().name(),
        message == null ? null : new Message(message.subject(), message.html(), message.text()),
        n.attempts().stream()
            .map(a -> new AttemptResponse(a.number(), a.at(), a.outcome().name(), a.reason()))
            .toList(),
        n.createdAt(),
        n.sentAt(),
        n.nextAttemptAt());
  }
}
