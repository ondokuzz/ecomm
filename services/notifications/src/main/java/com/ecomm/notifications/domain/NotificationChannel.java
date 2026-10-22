package com.ecomm.notifications.domain;

import java.util.List;

/**
 * How a Notification reaches its Customer. Each channel's message has its own parts, and each has a
 * {@code NotificationProvider} that sends it (ADR 0005). Email is always on; SMS and push are sent
 * only to a Customer whose Notification preferences opt in.
 */
public enum NotificationChannel {
  /** A subject, and the body twice: as HTML and as plain text. */
  EMAIL(List.of("subject", "html", "text"), Integer.MAX_VALUE),
  /** A plain-text body that fits one SMS: 160 characters. */
  SMS(List.of("text"), 160),
  /** A title and a plain-text body. */
  PUSH(List.of("title", "text"), Integer.MAX_VALUE);

  private final List<String> parts;
  private final int maxTextLength;

  NotificationChannel(List<String> parts, int maxTextLength) {
    this.parts = parts;
    this.maxTextLength = maxTextLength;
  }

  /** The parts a message on this channel has, each its own template. */
  public List<String> parts() {
    return parts;
  }

  /** The most characters its text part may have. */
  public int maxTextLength() {
    return maxTextLength;
  }

  /**
   * {@code message} cut to fit: an SMS longer than 160 characters ends at 159 and an ellipsis. Its
   * templates are checked to fit with their sample content, so only unusually long content is cut.
   */
  public RenderedMessage fit(RenderedMessage message) {
    var text = message.text();
    if (text == null || text.length() <= maxTextLength) {
      return message;
    }
    return new RenderedMessage(
        message.subject(), message.html(), text.substring(0, maxTextLength - 1) + "…");
  }

  /**
   * A Recipient's address on this channel as reads show it: an email address's local part but its
   * first character hidden ({@code a***@example.com}), a mobile number's digits but its first two
   * and its last two ({@code +49***78}). Push goes to the Customer, whose ID is shown.
   */
  public String mask(String address) {
    if (address == null) {
      return null;
    }
    return switch (this) {
      case EMAIL -> {
        var at = address.lastIndexOf('@');
        yield at <= 0 ? "***" : address.charAt(0) + "***" + address.substring(at);
      }
      case SMS ->
          address.length() <= 5
              ? "***"
              : address.substring(0, 3) + "***" + address.substring(address.length() - 2);
      case PUSH -> address;
    };
  }
}
