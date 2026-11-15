package com.ecomm.notifications.application;

import com.ecomm.notifications.application.port.in.UnsubscribeUseCase;
import com.ecomm.notifications.application.port.out.NotificationPreferencesStore;
import com.ecomm.notifications.application.port.out.TimeSource;
import com.ecomm.notifications.domain.UnsubscribeTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Cart reminder's unsubscribe link turns the Customer's cart reminders off, leaving the rest of
 * their Notification preferences as they were. Preferences already off aren't saved again, so their
 * {@code updatedAt} stays.
 */
public class UnsubscribeService implements UnsubscribeUseCase {

  private static final Logger log = LoggerFactory.getLogger(UnsubscribeService.class);

  private final UnsubscribeTokens tokens;
  private final NotificationPreferencesStore preferences;
  private final TimeSource time;

  public UnsubscribeService(
      UnsubscribeTokens tokens, NotificationPreferencesStore preferences, TimeSource time) {
    this.tokens = tokens;
    this.preferences = preferences;
    this.time = time;
  }

  @Override
  public boolean isValid(String token) {
    return tokens.customerOf(token, time.now()).isPresent();
  }

  @Override
  public void unsubscribeFromCartReminders(String token) {
    var now = time.now();
    var customerId = tokens.customerOf(token, now).orElseThrow(InvalidTokenException::new);
    var current = preferences.preferencesOf(customerId);
    if (current.cartReminders()) {
      preferences.save(current.withoutCartReminders(now));
      log.info("Customer {} unsubscribed from cart reminders", customerId);
    }
  }
}
