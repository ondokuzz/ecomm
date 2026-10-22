package com.ecomm.notifications.application;

import com.ecomm.notifications.application.port.in.PreferencesUseCase;
import com.ecomm.notifications.application.port.out.NotificationPreferencesStore;
import com.ecomm.notifications.application.port.out.TimeSource;
import com.ecomm.notifications.domain.NotificationPreferences;

public class PreferencesService implements PreferencesUseCase {

  private final NotificationPreferencesStore preferences;
  private final TimeSource time;

  public PreferencesService(NotificationPreferencesStore preferences, TimeSource time) {
    this.preferences = preferences;
    this.time = time;
  }

  @Override
  public NotificationPreferences preferencesOf(String customerId) {
    return preferences.preferencesOf(customerId);
  }

  @Override
  public NotificationPreferences save(
      String customerId, String phoneNumber, boolean sms, boolean push) {
    var saved = NotificationPreferences.of(customerId, phoneNumber, sms, push, time.now());
    preferences.save(saved);
    return saved;
  }
}
