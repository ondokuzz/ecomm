package com.ecomm.notifications.domain;

import java.util.Map;

/**
 * A message rendered from its templates, as it is sent: for email, its subject and its HTML and
 * text bodies; for SMS, its text; for push, its title, kept as the subject, and its text. A Message
 * type a Customer opted in to has an {@code unsubscribeUrl}, which email sends as its {@code
 * List-Unsubscribe} header (RFC 8058); it isn't stored, so a message read back has none.
 */
public record RenderedMessage(String subject, String html, String text, String unsubscribeUrl) {

  public RenderedMessage(String subject, String html, String text) {
    this(subject, html, text, null);
  }

  /** This message, sent with {@code url} as its unsubscribe link. */
  public RenderedMessage withUnsubscribeUrl(String url) {
    return new RenderedMessage(subject, html, text, url);
  }

  /** The message from its parts, named as its channel names them. */
  public static RenderedMessage of(Map<String, String> parts) {
    var subject = parts.containsKey("title") ? parts.get("title") : parts.get("subject");
    return new RenderedMessage(subject, parts.get("html"), parts.get("text"));
  }
}
