package com.ecomm.notifications.application.port.in;

import com.ecomm.notifications.domain.NotificationChannel;

/** What the mock SMS and push providers would have sent, for Staff. */
public interface MockProviderReadsUseCase {

  /**
   * A page of what the mock provider of {@code channel} took for the Customer, newest first.
   *
   * @throws IllegalArgumentException for {@code EMAIL}, whose provider isn't a mock
   */
  Page<MockedMessage> sent(NotificationChannel channel, String customerId, int page, int size);
}
