package com.ecomm.notifications.application.port.out;

import com.ecomm.notifications.domain.NotificationChannel;
import com.ecomm.notifications.domain.Recipient;
import com.ecomm.notifications.domain.RenderedMessage;
import com.ecomm.notifications.domain.SendOutcome;

/**
 * Sends a rendered message on one Notification channel (ADR 0005): email over SMTP for now. It
 * answers the provider's reference, or a transient or permanent failure; it never throws for a
 * message it couldn't send.
 */
public interface NotificationProvider {

  NotificationChannel channel();

  SendOutcome send(Recipient recipient, RenderedMessage message);
}
