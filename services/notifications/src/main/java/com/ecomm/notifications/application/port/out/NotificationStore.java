package com.ecomm.notifications.application.port.out;

import com.ecomm.notifications.application.port.in.NotificationFilter;
import com.ecomm.notifications.application.port.in.Page;
import com.ecomm.notifications.domain.Notification;
import java.time.Instant;
import java.util.Optional;

/** The Notification ledger. */
public interface NotificationStore {

  /**
   * Records it, unless one of the same event, Message type and channel is recorded already.
   *
   * @return whether it was recorded
   */
  boolean recordIfNew(Notification notification);

  /**
   * The {@code PENDING} Notification due soonest at {@code now}, locked until the transaction ends;
   * one another transaction holds is skipped.
   */
  Optional<Notification> nextDue(Instant now);

  /** Saves its status, Recipient, message and attempts. */
  void update(Notification notification);

  /** A page of the Notifications {@code filter} names, newest first. */
  Page<Notification> find(NotificationFilter filter, int page, int size);
}
