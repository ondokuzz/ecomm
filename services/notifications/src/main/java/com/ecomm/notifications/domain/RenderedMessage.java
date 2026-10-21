package com.ecomm.notifications.domain;

import java.util.Map;

/**
 * A message rendered from its templates, as it is sent: for email, its subject and its HTML and
 * text bodies.
 */
public record RenderedMessage(String subject, String html, String text) {

  /** The message from its parts, named as its channel names them. */
  public static RenderedMessage of(Map<String, String> parts) {
    return new RenderedMessage(parts.get("subject"), parts.get("html"), parts.get("text"));
  }
}
