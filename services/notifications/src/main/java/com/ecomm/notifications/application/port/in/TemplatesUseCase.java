package com.ecomm.notifications.application.port.in;

import com.ecomm.notifications.domain.MessageType;
import com.ecomm.notifications.domain.NotificationChannel;
import com.ecomm.notifications.domain.RenderedMessage;
import java.util.List;
import java.util.Locale;

/** Previews of the Message templates, each rendered with its Message type's sample content. */
public interface TemplatesUseCase {

  record Preview(
      MessageType type, NotificationChannel channel, Locale locale, RenderedMessage message) {}

  /** Every Message type on every channel, in {@code en}, in that order. */
  List<Preview> previews();

  /** One Message type on one channel, in the locale its Recipient would get for {@code locale}. */
  Preview preview(MessageType type, NotificationChannel channel, Locale locale);
}
