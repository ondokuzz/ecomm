package com.ecomm.notifications.domain;

import java.util.Map;

/**
 * A message rendered from its templates, as it is sent: for email, its subject and its HTML and
 * text bodies; for SMS, its text; for push, its title, kept as the subject, and its text.
 */
public record RenderedMessage(String subject, String html, String text) {

  /** The message from its parts, named as its channel names them. */
  public static RenderedMessage of(Map<String, String> parts) {
    var subject = parts.containsKey("title") ? parts.get("title") : parts.get("subject");
    return new RenderedMessage(subject, parts.get("html"), parts.get("text"));
  }
}
