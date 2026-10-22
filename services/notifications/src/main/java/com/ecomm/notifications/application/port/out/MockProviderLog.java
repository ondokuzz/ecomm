package com.ecomm.notifications.application.port.out;

import com.ecomm.notifications.application.port.in.MockedMessage;
import com.ecomm.notifications.application.port.in.Page;
import com.ecomm.notifications.domain.NotificationChannel;

/**
 * What the mock SMS and push providers would have sent, as they record it (ADR 0005). A real SMS
 * gateway or push service keeps its own.
 */
public interface MockProviderLog {

  /** A page of what the mock provider of {@code channel} took for the Customer, newest first. */
  Page<MockedMessage> find(NotificationChannel channel, String customerId, int page, int size);
}
