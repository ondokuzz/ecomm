package com.ecomm.notifications.application;

import com.ecomm.notifications.application.port.in.NotificationFilter;
import com.ecomm.notifications.application.port.in.NotificationReadsUseCase;
import com.ecomm.notifications.application.port.in.Page;
import com.ecomm.notifications.application.port.out.NotificationStore;
import com.ecomm.notifications.domain.Notification;

public class NotificationReadsService implements NotificationReadsUseCase {

  private final NotificationStore notifications;

  public NotificationReadsService(NotificationStore notifications) {
    this.notifications = notifications;
  }

  @Override
  public Page<Notification> notifications(NotificationFilter filter, int page, int size) {
    return notifications.find(filter, page, size);
  }
}
