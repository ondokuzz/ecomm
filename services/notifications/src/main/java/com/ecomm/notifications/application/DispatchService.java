package com.ecomm.notifications.application;

import com.ecomm.notifications.application.port.in.DispatchUseCase;
import com.ecomm.notifications.application.port.out.CustomerDirectory;
import com.ecomm.notifications.application.port.out.CustomerDirectory.DirectoryUnavailableException;
import com.ecomm.notifications.application.port.out.MessageTemplates;
import com.ecomm.notifications.application.port.out.NotificationPreferencesStore;
import com.ecomm.notifications.application.port.out.NotificationProvider;
import com.ecomm.notifications.application.port.out.NotificationStore;
import com.ecomm.notifications.application.port.out.TimeSource;
import com.ecomm.notifications.application.port.out.Transactions;
import com.ecomm.notifications.domain.Messages;
import com.ecomm.notifications.domain.Notification;
import com.ecomm.notifications.domain.NotificationChannel;
import com.ecomm.notifications.domain.RetryPolicy;
import com.ecomm.notifications.domain.SendOutcome;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends each due {@code PENDING} Notification in a transaction of its own, holding its row so no
 * other dispatcher sends it too: looks up its Recipient, renders its message in their locale, and
 * hands it to its channel's provider. A Customer the directory doesn't know, or one without an
 * address on its channel, fails it permanently as {@code noRecipient}, and one whose Notification
 * preferences no longer allow its channel as {@code optedOut}; a directory that can't answer, or a
 * provider that throws, fails it transiently. An SMS is cut to fit 160 characters.
 */
public class DispatchService implements DispatchUseCase {

  private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

  private final NotificationStore notifications;
  private final NotificationPreferencesStore preferences;
  private final CustomerDirectory directory;
  private final MessageTemplates templates;
  private final Map<NotificationChannel, NotificationProvider> providers;
  private final RetryPolicy retries;
  private final Transactions transactions;
  private final TimeSource time;

  public DispatchService(
      NotificationStore notifications,
      NotificationPreferencesStore preferences,
      CustomerDirectory directory,
      MessageTemplates templates,
      List<NotificationProvider> providers,
      RetryPolicy retries,
      Transactions transactions,
      TimeSource time) {
    this.notifications = notifications;
    this.preferences = preferences;
    this.directory = directory;
    this.templates = templates;
    this.providers = new EnumMap<>(NotificationChannel.class);
    for (var provider : providers) {
      if (this.providers.put(provider.channel(), provider) != null) {
        throw new IllegalStateException("Two NotificationProviders for " + provider.channel());
      }
    }
    for (var channel : NotificationChannel.values()) {
      if (!this.providers.containsKey(channel)) {
        throw new IllegalStateException("No NotificationProvider for " + channel);
      }
    }
    this.retries = retries;
    this.transactions = transactions;
    this.time = time;
  }

  @Override
  public int dispatchDue() {
    var tried = 0;
    while (transactions.inTransaction(this::dispatchNext)) {
      tried++;
    }
    return tried;
  }

  private boolean dispatchNext() {
    var due = notifications.nextDue(time.now());
    if (due.isEmpty()) {
      return false;
    }
    var notification = due.get();
    attempt(notification);
    notifications.update(notification);
    var last = notification.attempts().getLast();
    log.info(
        "{} {} Notification {} for Customer {}: attempt {} {}{}",
        notification.type(),
        notification.channel(),
        notification.id(),
        notification.customerId(),
        last.number(),
        last.outcome(),
        last.reason() == null ? "" : " (" + last.reason() + "), now " + notification.status());
    return true;
  }

  private void attempt(Notification notification) {
    var now = time.now();
    var channel = notification.channel();
    try {
      var preferred = preferences.preferencesOf(notification.customerId());
      if (!preferred.allows(channel)) {
        notification.failed(now, null, null, SendOutcome.Failed.permanent("optedOut"), retries);
        return;
      }
      var recipient =
          directory
              .find(notification.customerId())
              .map(r -> r.withPhoneNumber(preferred.phoneNumber()))
              .orElse(null);
      if (recipient == null || recipient.addressOn(channel) == null) {
        notification.failed(now, null, null, SendOutcome.Failed.permanent("noRecipient"), retries);
        return;
      }
      var locale = templates.localeFor(recipient.locale());
      var message =
          channel.fit(
              templates.render(
                  notification.type(),
                  channel,
                  locale,
                  Messages.model(notification.content(), recipient, locale)));
      switch (providers.get(channel).send(recipient, message)) {
        case SendOutcome.Sent sent -> notification.sent(now, recipient, message, sent.reference());
        case SendOutcome.Failed failed ->
            notification.failed(now, recipient, message, failed, retries);
      }
    } catch (DirectoryUnavailableException e) {
      notification.failed(
          now, null, null, SendOutcome.Failed.transientFailure("directoryUnavailable"), retries);
    } catch (RuntimeException e) {
      log.warn("Sending Notification {} failed unexpectedly", notification.id(), e);
      notification.failed(
          now, null, null, SendOutcome.Failed.transientFailure("unexpectedError"), retries);
    }
  }
}
