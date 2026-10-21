package com.ecomm.notifications.domain;

import java.util.List;

/**
 * How a Notification reaches its Customer. Each channel's message has its own parts, and each has a
 * {@code NotificationProvider} that sends it (ADR 0005).
 */
public enum NotificationChannel {
  /** A subject, and the body twice: as HTML and as plain text. */
  EMAIL(List.of("subject", "html", "text"));

  private final List<String> parts;

  NotificationChannel(List<String> parts) {
    this.parts = parts;
  }

  /** The parts a message on this channel has, each its own template. */
  public List<String> parts() {
    return parts;
  }
}
