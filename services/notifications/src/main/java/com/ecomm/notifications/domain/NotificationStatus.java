package com.ecomm.notifications.domain;

/** Where a Notification is: still to be sent, sent, or given up on. */
public enum NotificationStatus {
  PENDING,
  SENT,
  FAILED
}
