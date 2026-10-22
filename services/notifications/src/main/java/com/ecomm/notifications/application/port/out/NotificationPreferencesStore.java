package com.ecomm.notifications.application.port.out;

import com.ecomm.notifications.domain.NotificationPreferences;

/** Customers' Notification preferences. */
public interface NotificationPreferencesStore {

  /** The Customer's, or the defaults (email only) when they never saved any. */
  NotificationPreferences preferencesOf(String customerId);

  /** Keeps them, in place of any the Customer saved before. */
  void save(NotificationPreferences preferences);
}
