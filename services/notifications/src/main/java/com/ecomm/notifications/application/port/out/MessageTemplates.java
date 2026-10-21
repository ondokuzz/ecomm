package com.ecomm.notifications.application.port.out;

import com.ecomm.notifications.domain.MessageType;
import com.ecomm.notifications.domain.NotificationChannel;
import com.ecomm.notifications.domain.RenderedMessage;
import java.util.Locale;
import java.util.Map;

/**
 * The Message templates: one per Message type, Notification channel and locale, every Message type
 * having one for every channel in {@code en}.
 */
public interface MessageTemplates {

  /** The locale a Recipient reading {@code wanted} gets: it, its language, or else {@code en}. */
  Locale localeFor(Locale wanted);

  /** The message of {@code type} on {@code channel}, in a locale {@link #localeFor} gave. */
  RenderedMessage render(
      MessageType type, NotificationChannel channel, Locale locale, Map<String, Object> model);
}
