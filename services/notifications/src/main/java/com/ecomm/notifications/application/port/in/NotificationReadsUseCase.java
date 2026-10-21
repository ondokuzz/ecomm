package com.ecomm.notifications.application.port.in;

import com.ecomm.notifications.domain.Notification;

/** Reads the Notification ledger. */
public interface NotificationReadsUseCase {

  /** A page of the Notifications {@code filter} names, newest first. */
  Page<Notification> notifications(NotificationFilter filter, int page, int size);
}
