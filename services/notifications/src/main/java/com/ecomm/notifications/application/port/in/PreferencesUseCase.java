package com.ecomm.notifications.application.port.in;

import com.ecomm.notifications.domain.NotificationPreferences;

/** A Customer's own Notification preferences. */
public interface PreferencesUseCase {

  /** Theirs, or the defaults (email only) when they never saved any. */
  NotificationPreferences preferencesOf(String customerId);

  /**
   * Saves theirs.
   *
   * @throws NotificationPreferences.InvalidPhoneNumberException for a mobile number that isn't
   *     E.164, or SMS on without one
   */
  NotificationPreferences save(String customerId, String phoneNumber, boolean sms, boolean push);
}
